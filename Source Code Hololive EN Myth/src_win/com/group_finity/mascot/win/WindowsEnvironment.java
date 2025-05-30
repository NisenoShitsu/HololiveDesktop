package com.group_finity.mascot.win;

import com.group_finity.mascot.Main;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.group_finity.mascot.environment.Area;
import com.group_finity.mascot.environment.Environment;
import com.group_finity.mascot.win.jna.Dwmapi;
import com.group_finity.mascot.win.jna.Gdi32;
import com.group_finity.mascot.win.jna.RECT;
import com.group_finity.mascot.win.jna.User32;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;

import java.util.logging.*;

/**
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by HololiveEN Myth Shimeji-ee Group.
 */
class WindowsEnvironment extends Environment
{
    private static HashMap<Pointer, Boolean> ieCache = new LinkedHashMap<Pointer, Boolean>();
    
    public static Area workArea = new Area();
    
    public static Area activeIE = new Area();
    
    private static Pointer activeIEobject = null;

    private static String[ ] windowTitles = null;
    
    private enum IEResult { INVALID, NOT_IE, IE_OUT_OF_BOUNDS, IE };
    
    private static final Logger log = Logger.getLogger( Environment.class.getName( ) );
        
    private static boolean isIE( final Pointer ie )
    {
        final Boolean cachedValue = ieCache.get( ie );
        if( cachedValue != null )
            return cachedValue;

        final char[] title = new char[ 1024 ];

        final int titleLength = User32.INSTANCE.GetWindowTextW( ie, title, 1024 );
        
        final String ieTitle = new String( title, 0, titleLength );
        
        // optimisation to remove empty windows from consideration without the loop.
        // Program Manager hard coded exception as there's issues if we mess with it
        if( ieTitle.isEmpty( ) || ieTitle.equals( "Program Manager" ) )
        {
            ieCache.put( ie, false );
            return false;
        }

        if( windowTitles == null )
        {
            windowTitles = Main.getInstance( ).getProperties( ).getProperty( "InteractiveWindows", "" ).split( "/" );
        }
        
        for( String windowTitle : windowTitles )
        {
            if( !windowTitle.trim( ).isEmpty( ) && ieTitle.contains( windowTitle ) )
            {
                //log.log( Level.INFO, String.format( "value %s is ie", new String( title, 0, titleLength ) ) );
                ieCache.put( ie, true );
                return true;
            }
        }
        
        //log.log( Level.INFO, String.format( "value %s is not ie", new String( title, 0, titleLength ) ) );
        ieCache.put( ie, false );
        return false;
    }
    
    private static IEResult isViableIE( Pointer ie )
    {
        if( User32.INSTANCE.IsWindowVisible( ie ) != 0 )
        {
            // metro apps can be closed or minimised and still be considered "visible" by User32
            // have to consider the new cloaked variable instead
            LongByReference flagsRef = new LongByReference( );
            NativeLong result = Dwmapi.INSTANCE.DwmGetWindowAttribute( ie, Dwmapi.DWMWA_CLOAKED, flagsRef, 8 );
            if( result.longValue( ) != 0x80070057 && ( result.longValue( ) != 0 || flagsRef.getValue( ) != 0 ) ) // unsupported on 7 so skip the check
                return IEResult.NOT_IE;
            
            //int flags = User32.INSTANCE.GetWindowLongW( ie, User32.GWL_STYLE );
            //if( ( flags & User32.WS_MAXIMIZE ) != 0 )
                //return IEResult.INVALID;
            
            if( User32.INSTANCE.IsZoomed( ie ) != 0 )
                return IEResult.INVALID;

            if( isIE( ie ) && ( User32.INSTANCE.IsIconic( ie ) == 0 ) )
            {
                Rectangle ieRect = getIERect( ie );
                if( ieRect.intersects( getScreenRect( ) ) )
                    return IEResult.IE;
                else
                    return IEResult.IE_OUT_OF_BOUNDS;
            }
        }
        
        return IEResult.NOT_IE;
    }

    private static Pointer findActiveIE()
    {
        activeIEobject = null;
        
        User32.INSTANCE.EnumWindows( new User32.WNDENUMPROC( )
        {
            @Override
            public boolean callback( Pointer ie, Pointer data )
            {
                switch( isViableIE( ie ) )
                {
                    case IE:
                        activeIEobject = ie;
                        return false;

                    case IE_OUT_OF_BOUNDS:
                    case NOT_IE: // Valid window but not interactive according to user settings
                        return true;

                    case INVALID: // Something invalid is the foreground object
                    default:
                        activeIEobject = null;
                        return false;
                }
            }
        }, null );
        
        return activeIEobject;
        
//        Pointer ie = User32.INSTANCE.GetWindow( User32.INSTANCE.GetForegroundWindow(), User32.GW_HWNDFIRST );
//        Boolean continueFlag = true;
//
//        while( continueFlag && User32.INSTANCE.IsWindow( ie ) != 0 )
//        {
//            switch( isViableIE( ie ) )
//            {
//                case IE:
//                    return ie;
//                
//                case IE_OUT_OF_BOUNDS:
//                case NOT_IE: // Valid window but not interactive according to user settings
//                    ie = User32.INSTANCE.GetWindow( ie, User32.GW_HWNDNEXT );
//                    break;
//                
//                case INVALID: // Something invalid is the foreground object
//                    continueFlag = false;
//                    break;
//            }
//        }
//
//        return null;
    }

    private static Rectangle getIERect( Pointer ie )
    {
        final RECT out = new RECT();
        User32.INSTANCE.GetWindowRect( ie, out );
        final RECT in = new RECT();
        if( getWindowRgnBox( ie, in ) == User32.ERROR )
        {
            //log.log( Level.INFO, "getWindowRgnBox == User32.ERROR" );
            in.left = 0;
            in.top = 0;
            in.right = out.right - out.left;
            in.bottom = out.bottom - out.top;
        }
        return new Rectangle( out.left + in.left, out.top + in.top, in.Width( ), in.Height( ) );
    }

    private static int getWindowRgnBox( final Pointer window, final RECT rect )
    {

        Pointer hRgn = Gdi32.INSTANCE.CreateRectRgn( 0, 0, 0, 0 );
        try
        {
            if( User32.INSTANCE.GetWindowRgn( window, hRgn ) == User32.ERROR )
            {
                return User32.ERROR;
            }
            Gdi32.INSTANCE.GetRgnBox( hRgn, rect );
            return 1;
        }
        finally
        {
            Gdi32.INSTANCE.DeleteObject( hRgn );
        }
    }

    private static boolean moveIE( final Pointer ie, final Rectangle rect )
    {

        if( ie == null )
        {
            return false;
        }

        final RECT out = new RECT();
        User32.INSTANCE.GetWindowRect( ie, out );
        final RECT in = new RECT();
        if( getWindowRgnBox( ie, in ) == User32.ERROR )
        {
            //log.log( Level.INFO, "getWindowRgnBox == User32.ERROR" );
            in.left = 0;
            in.top = 0;
            in.right = out.right - out.left;
            in.bottom = out.bottom - out.top;
        }

        User32.INSTANCE.MoveWindow( ie, rect.x - in.left, rect.y - in.top, rect.width + out.Width() - in.Width(),
                                    rect.height + out.Height() - in.Height(), 1 );

        return true;
    }

    private static void restoreAllIEs( )
    {
        User32.INSTANCE.EnumWindows( new User32.WNDENUMPROC( )
        {
            int offset = 25;
            
            @Override
            public boolean callback( Pointer ie, Pointer data )
            {
                IEResult result = isViableIE( ie );
                if( result == IEResult.IE_OUT_OF_BOUNDS )
                {
                    final RECT workArea = new RECT( );
                    User32.INSTANCE.SystemParametersInfoW( User32.SPI_GETWORKAREA, 0, workArea, 0 );
                    final RECT rect = new RECT( );
                    User32.INSTANCE.GetWindowRect( ie, rect );
                    
                    rect.OffsetRect( workArea.left + offset - rect.left, workArea.top + offset - rect.top );
                    User32.INSTANCE.MoveWindow( ie, rect.left, rect.top, rect.Width( ), rect.Height( ), 1 );
                    User32.INSTANCE.BringWindowToTop( ie );
                    
                    offset += 25;
                }
                
                return true;
            }
        }, null );
    }

    @Override
    public void tick()
    {
        super.tick();
        workArea.set( getWorkAreaRect() );

        final Rectangle ieRect = getIERect( findActiveIE( ) );
        activeIE.setVisible( ( ieRect != null ) && ieRect.intersects( getScreen().toRectangle() ) );
        activeIE.set( ieRect == null ? new Rectangle( -1, -1, 0, 0 ) : ieRect );
    }

    @Override
    public void dispose( )
    {
    }

    @Override
    public void moveActiveIE( final Point point )
    {
        moveIE( findActiveIE(), new Rectangle( point.x, point.y, activeIE.getWidth(), activeIE.getHeight() ) );
    }

    @Override
    public void restoreIE()
    {
        restoreAllIEs();
    }

    @Override
    public Area getWorkArea()
    {
        return workArea;
    }

    @Override
    public Area getActiveIE()
    {
        return activeIE;
    }
    
    @Override
    public String getActiveIETitle( )
    {
        final Pointer ie = findActiveIE( );
        
        final char[] title = new char[ 1024 ];

        final int titleLength = User32.INSTANCE.GetWindowTextW( ie, title, 1024 );
        
        return new String( title, 0, titleLength );
    }
    
    private static Rectangle getWorkAreaRect( )
    {
        final RECT rect = new RECT();
        User32.INSTANCE.SystemParametersInfoW( User32.SPI_GETWORKAREA, 0, rect, 0 );
        return new Rectangle( rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top );
    }

    @Override
    public void refreshCache( )
    {
        ieCache.clear( ); // will be repopulated next isIE call
        windowTitles = null;
    }
    
//    private void dumpWindowInformation( )
//    {
//        final StringBuilder text = new StringBuilder( );
//        final char[] title = new char[ 1024 ];
//        User32.INSTANCE.EnumWindows( new User32.WNDENUMPROC( )
//        {
//            @Override
//            public boolean callback( Pointer ie, Pointer data )
//            {
//                int titleLength = User32.INSTANCE.GetWindowTextW( ie, title, 1024 );
//
//                String ieTitle = new String( title, 0, titleLength );
//
//                text.append( ieTitle ).append( " " ).append( isViableIE( ie ) ).append( "\r\n" );
//                return true;
//            }
//        }, null );
//        
//        try
//        {
//            PrintWriter out = new PrintWriter( "window-debug-information.txt" );
//            out.println( text.toString( ) );
//            out.close( );
//        }
//        catch( Exception e )
//        {
//        }
//    }
}
