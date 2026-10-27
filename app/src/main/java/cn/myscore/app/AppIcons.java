package cn.myscore.app;

import android.graphics.Path;

/** Functional icons on one 24-unit grid, drawn with Ui.Icon's rounded 1.7-unit stroke. */
final class AppIcons {
    private AppIcons() {}

    static Path path(String kind) {
        Path p=new Path();
        switch(kind) {
            case "external" -> {
                p.moveTo(14,3);p.lineTo(21,3);p.lineTo(21,10);
                p.moveTo(21,3);p.lineTo(11,13);
                p.moveTo(10,4);p.lineTo(5,4);p.quadTo(3,4,3,6);
                p.lineTo(3,19);p.quadTo(3,21,5,21);p.lineTo(18,21);p.quadTo(20,21,20,19);p.lineTo(20,14);
            }
            case "calendar-target" -> {
                calendar(p,21);
                p.moveTo(12,11);p.lineTo(12,17);
                p.moveTo(9,14);p.lineTo(12,17);p.lineTo(15,14);
            }
            case "subscriptions" -> {
                p.moveTo(8,3);p.lineTo(16,3);p.quadTo(18,3,18,5);
                p.lineTo(18,21);p.lineTo(12,17.5f);p.lineTo(6,21);p.lineTo(6,5);p.quadTo(6,3,8,3);p.close();
                p.moveTo(9,9.5f);p.lineTo(11,11.5f);p.lineTo(15,7.5f);
            }
            case "sync-subscriptions" -> {
                p.arcTo(3.5f,3.5f,20.5f,20.5f,-145,145,true);
                p.moveTo(3.5f,5);p.lineTo(5,7.2f);p.lineTo(7.7f,6.5f);
                p.arcTo(3.5f,3.5f,20.5f,20.5f,35,145,true);
                p.moveTo(16.3f,17.5f);p.lineTo(19,16.8f);p.lineTo(20.5f,19);
                p.moveTo(8.5f,11.8f);p.lineTo(11,14.3f);p.lineTo(15.5f,9.8f);
            }
            case "calendar-open" -> {
                // Leave the lower-right corner open for a distinct outward arrow.
                p.moveTo(10,21);p.lineTo(5.5f,21);p.quadTo(3.5f,21,3.5f,19);p.lineTo(3.5f,6.5f);p.quadTo(3.5f,4.5f,5.5f,4.5f);
                p.lineTo(18.5f,4.5f);p.quadTo(20.5f,4.5f,20.5f,6.5f);p.lineTo(20.5f,10);
                p.moveTo(8,2.5f);p.lineTo(8,6.5f);p.moveTo(16,2.5f);p.lineTo(16,6.5f);
                p.moveTo(3.5f,9);p.lineTo(20.5f,9);
                p.moveTo(13,21);p.lineTo(21,13);p.moveTo(15.5f,13);p.lineTo(21,13);p.lineTo(21,18.5f);
            }
            case "appearance" -> {
                p.moveTo(12,3);p.cubicTo(6.8f,3,3,6.8f,3,11.8f);p.cubicTo(3,17,7.1f,21,12,21);
                p.cubicTo(14.7f,21,15.4f,19,13.8f,17.3f);p.cubicTo(12.8f,16.2f,13.5f,14.5f,15.1f,14.5f);
                p.lineTo(17.4f,14.5f);p.cubicTo(20.1f,14.5f,21,12.6f,21,10.7f);p.cubicTo(21,6.2f,16.8f,3,12,3);p.close();
                p.addCircle(7.5f,10.2f,.7f,Path.Direction.CW);p.addCircle(11,7,.7f,Path.Direction.CW);p.addCircle(15.5f,8.3f,.7f,Path.Direction.CW);
            }
            case "app-update" -> {
                p.addCircle(12,12,9,Path.Direction.CW);
                p.moveTo(12,17);p.lineTo(12,7);p.moveTo(8,11);p.lineTo(12,7);p.lineTo(16,11);
            }
            case "sources" -> {
                p.addRoundRect(4,3,20,15,2,2,Path.Direction.CW);p.moveTo(4,9);p.lineTo(20,9);
                p.moveTo(8,6);p.lineTo(8.1f,6);p.moveTo(8,12);p.lineTo(8.1f,12);
                p.moveTo(12,15);p.lineTo(12,20);p.moveTo(5,18.5f);p.lineTo(5,21);p.moveTo(5,20);p.lineTo(19,20);p.moveTo(19,18.5f);p.lineTo(19,21);
            }
            case "help" -> {
                p.addCircle(12,12,9,Path.Direction.CW);
                p.moveTo(9.2f,8.7f);p.cubicTo(9.2f,5.5f,15.2f,5.5f,15.2f,8.8f);p.cubicTo(15.2f,10.7f,12,11,12,13.3f);
                p.moveTo(12,16.8f);p.lineTo(12,16.9f);
            }
            case "matches" -> {
                p.moveTo(7,3);p.lineTo(17,3);p.lineTo(17,9.5f);p.cubicTo(17,12.7f,14.8f,15,12,15);p.cubicTo(9.2f,15,7,12.7f,7,9.5f);p.close();
                p.moveTo(7,5);p.lineTo(3,5);p.lineTo(3,7);p.cubicTo(3,10,4.5f,11,7.2f,11);
                p.moveTo(17,5);p.lineTo(21,5);p.lineTo(21,7);p.cubicTo(21,10,19.5f,11,16.8f,11);
                p.moveTo(12,15);p.lineTo(12,21);p.moveTo(8,21);p.lineTo(16,21);
            }
            case "preferences" -> {
                for(int i=0;i<24;i++) {
                    double angle=Math.toRadians(-112.5+i*15);
                    float radius=(i%4==1||i%4==2)?9.5f:7.7f;
                    float x=12+(float)Math.cos(angle)*radius,y=12+(float)Math.sin(angle)*radius;
                    if(i==0)p.moveTo(x,y);else p.lineTo(x,y);
                }
                p.close();p.addCircle(12,12,3,Path.Direction.CW);
            }
            default -> {return null;}
        }
        return p;
    }

    private static void calendar(Path p,float bottom) {
        p.addRoundRect(3.5f,4.5f,20.5f,bottom,2,2,Path.Direction.CW);
        p.moveTo(8,2.5f);p.lineTo(8,6.5f);p.moveTo(16,2.5f);p.lineTo(16,6.5f);
        p.moveTo(3.5f,9);p.lineTo(20.5f,9);
    }
}
