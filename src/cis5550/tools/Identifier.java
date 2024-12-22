package cis5550.tools;

import java.time.Instant;
import java.util.Random;

public final class Identifier {
    private Identifier() {}
    
    private static final Random random = new Random();
    private static final String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    
    public static String genIdentifier() {
        StringBuilder sb = new StringBuilder();
        sb.append(Instant.now().toString());
        
        for (int i = 0; i < 20; i++) {
            int index = random.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }
        
        return sb.toString();
    }
}
