package com.whenhub.importer;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.gson.Gson;

public class Quickstart {
	/** Application name. */
	private static final String APPLICATION_NAME = "Drive API Java Quickstart";

	/** Directory to store user credentials for this application. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
			".credentials/drive-java-quickstart");

	/** Global instance of the {@link FileDataStoreFactory}. */
	private static FileDataStoreFactory DATA_STORE_FACTORY;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private static HttpTransport HTTP_TRANSPORT;

	/**
	 * Global instance of the scopes required by this quickstart.
	 *
	 * If modifying these scopes, delete your previously saved credentials at
	 * ~/.credentials/drive-java-quickstart
	 */
	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY,
			DriveScopes.DRIVE_READONLY);

	private static final String SUFFIX = "/";

	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Creates an authorized Credential object.
	 * 
	 * @return an authorized Credential object.
	 * @throws IOException
	 */
	public static Credential authorize() throws IOException {
		// Load client secrets.
		InputStream in = Quickstart.class.getResourceAsStream("/client_secret.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
		return credential;
	}

	/**
	 * Build and return an authorized Drive client service.
	 * 
	 * @return an authorized Drive client service
	 * @throws IOException
	 */
	public static Drive getDriveService() throws IOException {
		Credential credential = authorize();
		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
	}

	public static void importFile(String id) throws IOException {

		// Build a new authorized API client service.
		Drive service = getDriveService();

		// instantiate gson
		Gson gson = new Gson();

		// Print the names and IDs for up to 10 files.
		File file = service.files().get(id).setFields("id, name").execute();
		if (file == null) {
			System.out.println("No files found.");
		} else {

			System.out.printf("File: %s Id: (%s) Kind: %s \n", file.getName(), file.getId(), file.getKind());

			if (file.getId().equals(id)) {

				System.out.println("Exporting presentation: " + file.getName());

				OutputStream out = new FileOutputStream("Exported Presentation.pdf");

				service.files().export(file.getId(), "application/pdf").executeMediaAndDownloadTo(out);

				// export each page to image
				String pdfFilename = "Exported Presentation.pdf";
				List<java.io.File> imgFiles = exportPDFToImages(pdfFilename);

				// upload to Amazon S3
				List<String> whenhubImages = new ArrayList<String>();
			    String bucketName = "generatedpdf";
			    AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
			    AmazonS3 s3client = new AmazonS3Client(credentials);
			    for (java.io.File imgFile : imgFiles) {
			    	java.io.File tempFile = java.io.File.createTempFile("whenhub-images", ".png");
					PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, tempFile.getName(), imgFile)
							.withCannedAcl(CannedAccessControlList.PublicRead);
					s3client.putObject(putObjectRequest);
					// https://s3.amazonaws.com/generatedpdf/Exported+Presentation-0.png
					String url = "https://s3.amazonaws.com/generatedpdf/" + tempFile.getName();
					System.out.println("   download link " + url);
					whenhubImages.add(url);
			    }

				// me request
				HttpClient client = new DefaultHttpClient();
				HttpGet request = new HttpGet("https://api.whenhub.com/api/users/me?access_token=9GQKQ077Ed9gWOb7imK4r8QWhBjrtocXNHpuTtS5DKzrSfDgnTQZ4rMaXkmGyjh6");
				HttpResponse response = client.execute(request);
				BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				String resp = "";
				String line = "";
				while ((line = rd.readLine()) != null) {
					resp = resp + line;
				}
				System.out.println("   whenhub me response: " + resp);

				// create schedule
				HttpPost createScheduleRequest = new HttpPost("https://api.whenhub.com/api/users/me/schedules?access_token=9GQKQ077Ed9gWOb7imK4r8QWhBjrtocXNHpuTtS5DKzrSfDgnTQZ4rMaXkmGyjh6");
				StringEntity createScheduleInput = new StringEntity("{\"name\": \"" + file.getName() + "\", \"description\":\"Exported presentation\"}");
				createScheduleInput.setContentType("application/json");
				createScheduleRequest.setEntity(createScheduleInput);
				HttpResponse createScheduleResponse = client.execute(createScheduleRequest);
				BufferedReader scheduleBufferedReader = new BufferedReader(new InputStreamReader(createScheduleResponse.getEntity().getContent()));
				String createScheduleResponseString = "";
				String createScheduleResponseLine = "";
				while ((createScheduleResponseLine = scheduleBufferedReader.readLine()) != null) {
					createScheduleResponseString = createScheduleResponseString + createScheduleResponseLine;
				}
				Schedule schedule = gson.fromJson(createScheduleResponseString, Schedule.class);
				System.out.println("   whenhub create schedule response: " + schedule.toString());

				// create event
				DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
				Date date = new Date();
				String formattedDate = dateFormat.format(date);
				HttpPost createEventRequest = new HttpPost("https://api.whenhub.com/api/schedules/" + schedule.id + "/events?access_token=9GQKQ077Ed9gWOb7imK4r8QWhBjrtocXNHpuTtS5DKzrSfDgnTQZ4rMaXkmGyjh6");
				StringEntity createEventInput = new StringEntity("{\"when\": {     \"period\": \"minute\",     \"startDate\": \"" + formattedDate + "\"" + ",  \"startTimezone\": \"America/New_York\",     \"endDate\": null,     \"endTimezone\": \"America/New_York\"   }, \"name\": \"Slides\", \"description\":\"Exported slides\"}");
				createEventInput.setContentType("application/json");
				createEventRequest.setEntity(createEventInput);
				HttpResponse createEventResponse = client.execute(createEventRequest);
				BufferedReader eventBufferedReader = new BufferedReader(new InputStreamReader(createEventResponse.getEntity().getContent()));
				String createEventResponseString = "";
				String createEventResponseLine = "";
				while ((createEventResponseLine = eventBufferedReader.readLine()) != null) {
					createEventResponseString = createEventResponseString + createEventResponseLine;
				}
				Event event = gson.fromJson(createEventResponseString, Event.class);
				System.out.println("   whenhub create event response: " + createEventResponseString);
				System.out.println("   event: " + event.toString());

				// add images to event
				for (String url : whenhubImages) {
					HttpPost createEventImageRequest = new HttpPost("https://api.whenhub.com/api/events/" + event.id + "/media?access_token=9GQKQ077Ed9gWOb7imK4r8QWhBjrtocXNHpuTtS5DKzrSfDgnTQZ4rMaXkmGyjh6");
					StringEntity createEventImageInput = new StringEntity("{\"type\": \"image\", \"url\":\"" + url + "\"}");
					createEventImageInput.setContentType("application/json");
					createEventImageRequest.setEntity(createEventImageInput);
					HttpResponse createEventImageResponse = client.execute(createEventImageRequest);
					BufferedReader eventImageBufferedReader = new BufferedReader(new InputStreamReader(createEventImageResponse.getEntity().getContent()));
					String createEventImageResponseString = "";
					String createEventImageResponseLine = "";
					while ((createEventImageResponseLine = eventImageBufferedReader.readLine()) != null) {
						createEventImageResponseString = createEventImageResponseString + createEventImageResponseLine;
					}
					System.out.println("   whenhub create event response: " + createEventImageResponseString);
				}
			}
		}
	}

	private static List<java.io.File> exportPDFToImages(String pdfFilename) throws InvalidPasswordException, IOException {
		java.io.File pdfFile = new java.io.File(pdfFilename);
		PDDocument document = PDDocument.load(pdfFile);
		List<java.io.File> files = exportPDFToImages(pdfFilename, 1, document.getNumberOfPages());
		document.close();
		return files;
	}

	private static List<java.io.File> exportPDFToImages(String pdfFilename, int from, int to) throws InvalidPasswordException, IOException {
		List<java.io.File> files = new ArrayList<java.io.File>();
		java.io.File pdfFile = new java.io.File(pdfFilename);
		PDDocument document = PDDocument.load(pdfFile);
		PDFRenderer renderer = new PDFRenderer(document);
		for (int pageNum = from - 1; pageNum < to; pageNum++) {
			BufferedImage image1 = renderer.renderImage(pageNum);
			java.io.File file = new java.io.File(pdfFilename.replaceAll(".pdf", "-" + pageNum + ".png"));
			files.add(file);
			ImageIO.write(image1, "PNG", file);
		}
		document.close();
		return files;
	}
}

class CustomProgressListener implements MediaHttpDownloaderProgressListener {
	public void progressChanged(MediaHttpDownloader downloader) {
		switch (downloader.getDownloadState()) {
		case MEDIA_IN_PROGRESS:
			System.out.println(downloader.getProgress());
			break;
		case MEDIA_COMPLETE:
			System.out.println("Download is complete!");
		}
	}
}

class Schedule {
	public String id;
	
	public String toString() {
		return "id: " + this.id;
	}
}

class Event {
	public String id;
	
	public String toString() {
		return "id: " + this.id;
	}
}

