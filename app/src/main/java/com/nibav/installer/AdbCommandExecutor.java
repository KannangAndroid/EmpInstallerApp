package com.nibav.installer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class AdbCommandExecutor {

    public static String executeAdbCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return output.toString();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }
}