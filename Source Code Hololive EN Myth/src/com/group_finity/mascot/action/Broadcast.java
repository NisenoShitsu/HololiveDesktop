package com.group_finity.mascot.action;

import java.util.List;
import java.util.logging.Logger;

import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.script.VariableMap;

/**
 * Original Author: Yuki Yamada of Group Finity
 * (http://www.group-finity.com/Shimeji/) Currently developed by HololiveEN Myth Shimeji-ee
 * Group.
 */
@Deprecated
public class Broadcast extends Animate
{
    private static final Logger log = Logger.getLogger( Broadcast.class.getName( ) );

    public Broadcast( java.util.ResourceBundle schema, final List<Animation> animations, final VariableMap context )
    {
        super( schema, animations, context );
    }
}
