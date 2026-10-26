package cn.myscore.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Presentation rules shared by live values and restored detail caches. */
final class DetailFormat {
    private DetailFormat(){}
    static String number(String value,int decimals){
        try{return new BigDecimal(value).setScale(decimals,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
        catch(RuntimeException e){return value==null||value.isBlank()?"—":value;}
    }
    static String damage(String value){
        String rounded=number(value,0);
        try{return String.format(Locale.US,"%,d",Long.parseLong(rounded));}catch(RuntimeException e){return rounded;}
    }
    static String shortName(String full){
        if(full==null||full.isBlank())return "—";
        String[] parts=full.trim().split("\\s+");if(parts.length<2)return full;
        int last=parts.length-1;
        if(parts[last].matches("(?i)Jr\\.?|Sr\\.?|II|III|IV|V"))last--;
        if(last<1)return full;
        // Keep surname particles and suffixes with the family name.
        while(last>1&&parts[last-1].matches("(?i)de|del|da|dos|van|von|der|den|le|la|st\\.?"))last--;
        return new String(Character.toChars(parts[0].codePointAt(0)))+". "+String.join(" ",java.util.Arrays.copyOfRange(parts,last,parts.length));
    }
    /** 1 winner, -1 loser, 0 draw/unknown. Only confirmed final results are outcomes. */
    static int outcome(String own,String other,boolean finished){
        if(!finished)return 0;
        try{return new BigDecimal(own).compareTo(new BigDecimal(other));}catch(RuntimeException e){return 0;}
    }
}
