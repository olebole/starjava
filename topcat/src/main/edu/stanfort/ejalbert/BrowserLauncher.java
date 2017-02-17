package edu.stanford.ejalbert;

import java.io.IOException;

/*
 * Simple replacement for the BrowserLauncher, which is compatible to the
 * Free Desktop Consortium.
 */
public class BrowserLauncher {
    public void openURLinBrowser(String url)  {
	try {
	    Runtime.getRuntime().exec(new String[] { "xdg-open", url } );
	} catch (IOException e) {
	}
    }
    public void setNewWindowPolicy(boolean b) {
    }
}
