/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import paket.compiler.PAKETConfig;

/**
 *
 * @author santi
 */
public class ConsoleExecution {
    public static int execute(String[] args, PAKETConfig config) throws Exception
    {
        String tmp = "";
        for(String arg:args) tmp += arg + " ";
        config.info("Command line: " + tmp);
        Process p = Runtime.getRuntime().exec(args);
        
        p.waitFor();
        
        InputStream stdout = p.getInputStream();
        InputStream stderr = p.getErrorStream();
        BufferedReader stdout_br = new BufferedReader(new InputStreamReader(stdout));
        BufferedReader stderr_br = new BufferedReader(new InputStreamReader(stderr));
        while(stdout_br.ready()) {
            config.info(stdout_br.readLine());
        }
        while(stderr_br.ready()) {
            config.info(stderr_br.readLine());
        }
        
        return p.exitValue();
    }
}
