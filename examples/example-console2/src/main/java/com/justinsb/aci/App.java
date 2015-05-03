package com.justinsb.aci;

import java.util.List;

import org.apache.commons.math3.util.ArithmeticUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * Hello rkt world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        List<String> strings = Lists.newArrayList();
        strings.add("Hello");
        strings.add("rkt");
        strings.add("multi-jar");
        strings.add("World!");
        System.out.println( Joiner.on(" ").join(strings) );
        System.out.println( "FYI, the LCM of 20 and 64 is " + ArithmeticUtils.lcm(20, 64) );
    }
}
