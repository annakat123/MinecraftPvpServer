package dev.pvpbot.bot.ai.adaptation;

public final class PlayerBehaviorModel {
    private int samples; private double aggression=.5, lateralBias, jumpRate;
    public void observe(double closingSpeed,double lateralSpeed,boolean airborne){samples++; double alpha=Math.max(.02,1.0/Math.min(samples,50)); aggression=blend(aggression,clamp(.5+closingSpeed*3),alpha); lateralBias=blend(lateralBias,clampSigned(lateralSpeed*4),alpha); jumpRate=blend(jumpRate,airborne?1:0,alpha);}
    private static double blend(double a,double b,double alpha){return a+(b-a)*alpha;} private static double clamp(double x){return Math.max(0,Math.min(1,x));} private static double clampSigned(double x){return Math.max(-1,Math.min(1,x));}
    public double aggression(){return aggression;} public double lateralBias(){return lateralBias;} public double jumpRate(){return jumpRate;} public double confidence(){return Math.min(1,samples/20.0);}
}
