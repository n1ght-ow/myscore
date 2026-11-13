package cn.myscore.app;

import android.graphics.Path;

/** Functional icons on one 24-unit grid, drawn with Ui.Icon's rounded 1.75-unit stroke. */
final class AppIcons {
    private AppIcons() {}

    static Path path(String kind) {
        Path p=new Path();
        switch(kind) {
            case "external" -> {
                p.moveTo(13.5f,3.5f);p.lineTo(20.5f,3.5f);p.lineTo(20.5f,10.5f);
                p.moveTo(20.5f,3.5f);p.lineTo(11.5f,12.5f);
                p.moveTo(10.5f,4.5f);p.lineTo(6.2f,4.5f);p.quadTo(4f,4.5f,4f,6.7f);
                p.lineTo(4f,17.3f);p.quadTo(4f,19.5f,6.2f,19.5f);p.lineTo(17.3f,19.5f);p.quadTo(19.5f,19.5f,19.5f,17.3f);p.lineTo(19.5f,13.5f);
            }
            case "calendar-target" -> {
                calendar(p,20.5f);
                p.moveTo(12f,11.5f);p.lineTo(12f,17.2f);
                p.moveTo(9.5f,14.8f);p.lineTo(12f,17.2f);p.lineTo(14.5f,14.8f);
            }
            case "subscriptions" -> {
                p.moveTo(7.5f,3.5f);p.lineTo(16.5f,3.5f);p.quadTo(18.5f,3.5f,18.5f,5.5f);
                p.lineTo(18.5f,20.5f);p.lineTo(12f,16.8f);p.lineTo(5.5f,20.5f);p.lineTo(5.5f,5.5f);p.quadTo(5.5f,3.5f,7.5f,3.5f);p.close();
                p.moveTo(9.3f,9.8f);p.lineTo(11.2f,11.7f);p.lineTo(14.8f,8.1f);
            }
            case "sync-subscriptions" -> {
                p.arcTo(3.8f,3.8f,20.2f,20.2f,-155f,135f,true);
                p.moveTo(17.4f,7.8f);p.lineTo(19.7f,9.2f);p.lineTo(20.5f,6.6f);
                p.arcTo(3.8f,3.8f,20.2f,20.2f,25f,135f,true);
                p.moveTo(6.6f,16.2f);p.lineTo(4.3f,14.8f);p.lineTo(3.5f,17.4f);
                p.moveTo(9.2f,12.0f);p.lineTo(11.2f,14.0f);p.lineTo(15.0f,10.2f);
            }
            case "calendar-open" -> {
                p.moveTo(11f,20f);p.lineTo(6.5f,20f);p.quadTo(4f,20f,4f,17.5f);p.lineTo(4f,7.5f);p.quadTo(4f,5f,6.5f,5f);
                p.lineTo(17.5f,5f);p.quadTo(20f,5f,20f,7.5f);p.lineTo(20f,11f);
                p.moveTo(8.5f,3f);p.lineTo(8.5f,6.5f);p.moveTo(15.5f,3f);p.lineTo(15.5f,6.5f);
                p.moveTo(4f,9.2f);p.lineTo(20f,9.2f);
                p.moveTo(13.5f,19.5f);p.lineTo(19.5f,13.5f);p.moveTo(15.2f,13.5f);p.lineTo(19.5f,13.5f);p.lineTo(19.5f,17.8f);
            }
            case "calendar-cloud" -> {
                p.moveTo(7.2f,13.8f);p.lineTo(6.4f,13.8f);
                p.cubicTo(3.5f,13.8f,3.5f,9.0f,6.8f,8.8f);
                p.cubicTo(7.4f,4.2f,14.4f,3.8f,16.0f,8.0f);
                p.cubicTo(20.5f,8.2f,20.5f,13.8f,17.4f,13.8f);
                p.lineTo(16.8f,13.8f);
                p.moveTo(9.5f,20f);p.lineTo(9.5f,14.5f);p.moveTo(7.5f,16.5f);p.lineTo(9.5f,14.5f);p.lineTo(11.5f,16.5f);
                p.moveTo(14.5f,14.5f);p.lineTo(14.5f,20f);p.moveTo(12.5f,18.0f);p.lineTo(14.5f,20f);p.lineTo(16.5f,18.0f);
            }
            case "appearance" -> {
                p.addCircle(12f,12f,8.2f,Path.Direction.CW);
                p.moveTo(12f,3.8f);p.cubicTo(16.2f,5.6f,16.2f,18.4f,12f,20.2f);
            }
            case "app-update" -> {
                p.addCircle(12f,12f,8.5f,Path.Direction.CW);
                p.moveTo(12f,16.2f);p.lineTo(12f,8.0f);p.moveTo(8.8f,11.2f);p.lineTo(12f,8.0f);p.lineTo(15.2f,11.2f);
            }
            case "sources" -> {
                p.addRoundRect(4f,4.5f,20f,10.5f,2.2f,2.2f,Path.Direction.CW);
                p.addRoundRect(4f,13.5f,20f,19.5f,2.2f,2.2f,Path.Direction.CW);
                p.addCircle(7.5f,7.5f,0.8f,Path.Direction.CW);
                p.addCircle(7.5f,16.5f,0.8f,Path.Direction.CW);
                p.moveTo(11.5f,7.5f);p.lineTo(16.5f,7.5f);
                p.moveTo(11.5f,16.5f);p.lineTo(16.5f,16.5f);
            }
            case "help" -> {
                p.addCircle(12f,12f,8.5f,Path.Direction.CW);
                p.moveTo(9.6f,9.2f);p.cubicTo(9.6f,6.2f,14.6f,6.2f,14.6f,9.2f);p.cubicTo(14.6f,11.0f,12.0f,11.4f,12.0f,13.4f);
                p.addCircle(12f,16.7f,0.55f,Path.Direction.CW);
            }
            case "matches" -> {
                p.moveTo(6.5f,3.5f);p.lineTo(17.5f,3.5f);p.lineTo(17.5f,8.5f);
                p.cubicTo(17.5f,12.2f,15.0f,14.5f,12.0f,14.5f);
                p.cubicTo(9.0f,14.5f,6.5f,12.2f,6.5f,8.5f);p.close();
                p.moveTo(6.5f,5.5f);p.cubicTo(3.5f,5.5f,3.5f,10.2f,7.0f,10.8f);
                p.moveTo(17.5f,5.5f);p.cubicTo(20.5f,5.5f,20.5f,10.2f,17.0f,10.8f);
                p.moveTo(12f,14.5f);p.lineTo(12f,19.5f);p.moveTo(8.5f,19.5f);p.lineTo(15.5f,19.5f);
            }
            case "preferences" -> {
                p.moveTo(4f,8.5f);p.lineTo(7.8f,8.5f);p.addCircle(10.2f,8.5f,2.3f,Path.Direction.CW);p.moveTo(12.6f,8.5f);p.lineTo(20f,8.5f);
                p.moveTo(4f,15.5f);p.lineTo(11.4f,15.5f);p.addCircle(13.8f,15.5f,2.3f,Path.Direction.CW);p.moveTo(16.2f,15.5f);p.lineTo(20f,15.5f);
            }
            case "chevron-down" -> {
                p.moveTo(7f,9.5f);p.lineTo(12f,14.5f);p.lineTo(17f,9.5f);
            }
            default -> {return null;}
        }
        return p;
    }

    private static void calendar(Path p,float bottom) {
        p.addRoundRect(4f,5f,20f,bottom,2.5f,2.5f,Path.Direction.CW);
        p.moveTo(8.5f,3f);p.lineTo(8.5f,6.5f);p.moveTo(15.5f,3f);p.lineTo(15.5f,6.5f);
        p.moveTo(4f,9.2f);p.lineTo(20f,9.2f);
    }
}
