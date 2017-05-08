package com.whenhub.importer;
import static spark.Spark.*;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {

    	/**
    	 * Set server port
    	 */
    	port(Integer.parseInt(System.getProperty("server.port"))); // Spark will run on specified port

    	/**
    	 * Static file location
    	 * root is 'src/main/resources', so put files in 'src/main/resources/public'
    	 */
    	staticFileLocation("/public");

    	/**
    	    Import content from Google to WhenHub
    	    
    	    From Google Drive, get Shareable link.

    	    	A few public links from my Google Docs:
	
					Portfolio
					https://docs.google.com/presentation/d/1e3rofZLUdOj3FIrQZwd4YMFAT5nPJdQN49rORrTUsNc/edit?usp=sharing
					
					PhotoAlbum
					https://docs.google.com/presentation/d/1EdGQZlIg-KAIpz3zDeozmv6EJF-d9kEoDA0Vu8q6PO0/edit?usp=sharing
	
					This doesn't work as Export is available only from Google Docs
					https://drive.google.com/open?id=0B90cqa_3QU9-QUxHMGliQ1FnNWM
    	 */
    	
    	get("/import", (request, response) -> {
            Quickstart.importFile(request.queryParams("id"));
    	    return "Imported!";
    	});
    }
}
