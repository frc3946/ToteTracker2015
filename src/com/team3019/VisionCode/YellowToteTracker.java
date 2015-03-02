package com.team3019.VisionCode;

//import java.awt.image.BufferedImage;
//import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
//import java.net.URL;
//import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;

//import javax.imageio.ImageIO;

import org.apache.commons.net.ftp.FTPClient;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import edu.wpi.first.wpilibj.networktables.NetworkTable;

/**
 * @author: Elijah Kaufman from FRC team 3019, Firebird Robotics
 * 
 * Things to note
 * {@link} http://www.chiefdelphi.com/forums/showpost.php?p=1427940&postcount=3
 */


public class YellowToteTracker{
	
	static FTPClient ftp;
	
	static NetworkTable table;
	static Thread Capture;
	static Thread Process;
	static Thread Send;
	// Color constants
	public static final Scalar 
		Red = new Scalar(0, 0, 255),
		Blue = new Scalar(255, 0, 0),
		Green = new Scalar(0, 255, 0),
		Yellow = new Scalar(0, 255, 255),
		// For tape
		thresh_Lower = new Scalar(0,110,0),
		thresh_Higher = new Scalar(255,255,134),
		// For grey totes
		grey_Lower = new Scalar(48,60,35),
		grey_higher = new Scalar(81,84,54);
	
	static final boolean Process_Gray = false;
	public static ArrayList<MatOfPoint> contours = new ArrayList<>();
	public static Mat frame,grey,original;
	static int counter = 0;
	
	public static void main(String[] args) {
		// Required for openCV to work -called before any functions of oCV are used
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		// Initialize network tables
		NetworkTable.setClientMode();
		// The IP of the SmartDashboard is "roborio-####.local" where #### is the team number
		NetworkTable.setIPAddress("roborio-3946.local");
		table = NetworkTable.getTable("SmartDashboard");
		
		ftp = new FTPClient();
		try {
			ftp.connect("roborio-3946.local");
		} catch (SocketException e) {
			// TODO Auto-generated catch block
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Main loop of the program
		while(true){
			try {
				while(table.isConnected()){
					processImage();
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				break;
			}
		}
	}
	// Opens a new connection to the Axis/USB camera and opens a new snapshot "instance".
	public static void processImage(){
		try {
			// The url of the camera snapshot to save ##.## with your team number
			// URL url = new URL("http://10.##.##.11/axis-cgi/jpg/image.cgi");			
			
			// Comment if testing a regular file
			//URL url = new URL("http://10.30.19.11/axis-cgi/jpg/image.cgi");
			//URLConnection uc = url.openConnection();
			//BufferedImage image = ImageIO.read(us.getInputStream());
			
			FileOutputStream fstream = null;
			try {
				fstream = new FileOutputStream("frame.jpg");
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // true tells to append data
			
			try {
				ftp.retrieveFile("/home/lvuser/frame.jpg", fstream);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			fstream.close();
			// Time for the OpenCV fun!
			// frame = new Mat();
			grey = new Mat();
			original = Highgui.imread("frame.jpg");
			frame = original.clone();
			// grey = original;
			// Applies a threshold in the form of BlueGreenRed
			Core.inRange(original, thresh_Lower, thresh_Higher, frame);
			// Finds the cluster of particles.
			Imgproc.findContours(frame, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
			// Iterates through the list of contours and removes those with an "area" less than 100.
			for (Iterator<MatOfPoint> iterator = contours.iterator(); iterator.hasNext();) {
				MatOfPoint matOfPoint = (MatOfPoint) iterator.next();
				if(matOfPoint.width() * matOfPoint.height() < 20){
					iterator.remove();
				}
			}
			// If there's only one contour, skip the process of bounding rectangles.
			if(contours.size() == 1){
					Rect rec1 = Imgproc.boundingRect(contours.get(0));
					Core.rectangle(original, rec1.tl(), rec1.br(), Yellow);
					String string = "TargetFound at X:" + (rec1.tl().x + rec1.br().x) / 2 + "Y:" + (rec1.tl().y + rec1.br().y) / 2;
					Core.putText(original, string, new Point(200,frame.size().height-10), Core.FONT_HERSHEY_PLAIN, 1, Red);
			}
			// I wonder if there are two...
			else if(contours.size() == 2 || contours.size() == 4){
				ArrayList<Rect> target1 = new ArrayList<Rect>();
				ArrayList<Rect> target2 = new ArrayList<Rect>();
					target1.add(Imgproc.boundingRect(contours.get(0)));
					target1.add(Imgproc.boundingRect(contours.get(1)));
					if(contours.size() == 4){
						target2.add(Imgproc.boundingRect(contours.get(2)));
						target2.add(Imgproc.boundingRect(contours.get(3)));
					}
				Point tl = target1.get(0).tl();
				Point br = target1.get(0).br();
				// Perimeter of first rectangle
				for(Rect rec : target1){
					if(tl.x > rec.tl().x){
						tl.x = rec.tl().x;
					}
					if(tl.y > rec.tl().y){
						tl.y = rec.tl().y;
					}
					if(br.x < rec.br().x){
						br.x = rec.br().x;
					}
					if(br.y < rec.br().y){
						br.y = rec.br().y;
					}
					table.putBoolean("TargetVisible", true);
				}
				Rect bb1 = new Rect(tl, br);
				Core.rectangle(original, bb1.br(),bb1.tl(), Yellow);
				Rect bb2 = null;
				// The 4 lines will make 4 contours
				// If there were 4 contours, fill the perimeter of the second rectangle.
				if(!target2.isEmpty()){
					tl = target2.get(0).tl();
					br = target2.get(0).br();
					for(Rect rec : target2){
						if(tl.x > rec.tl().x){
							tl.x = rec.tl().x;
						}
						if(tl.y > rec.tl().y){
							tl.y = rec.tl().y;
						}
						if(br.x < rec.br().x){
							br.x = rec.br().x;
						}
						if(br.y < rec.br().y){
							br.y = rec.br().y;
						}
					}
					bb2 = new Rect(tl, br);
					table.putBoolean("TargetVisible", true);
				}
				String string;
				if(null != bb2){
					Core.rectangle(original, bb2.br(),bb2.tl(), Yellow);
					// Checks if the totes are stacked or simply within sight.
					if(Math.abs(bb1.x-bb2.x) < 5){
						string = "Two totes stacked.";
						Core.line(original, new Point(bb1.x+bb1.width/2,bb1.y+bb1.height/2),
								new Point(bb2.x+bb2.width/2,bb2.y+bb2.height/2), Green);
					}
					else{
					string = "Two totes found.";
					}
				}
				else{
					// By knowing how far off-center the tote is, you can set your robot to align with it (essentially turning towards it).
					string = " Tote off-center by " + (bb1.x + bb1.width/2 - 330) + " pixels.";
					table.putNumber("OffCenter", (bb1.x + bb1.width/2 - 330));
					table.putNumber("ToteToScreen", bb1.width / 640.0);
				}
				// Stores the data in a visible form
				Core.putText(original, string, new Point(200,frame.size().height-10), Core.FONT_HERSHEY_PLAIN, 1, Green);
				table.putBoolean("TargetVisible", true);
			}
			else if(contours.size() == 3){
				ArrayList<Rect> rect = new ArrayList<>();
				for(MatOfPoint mOP : contours){
					rect.add(Imgproc.boundingRect(mOP));
					Rect temp = rect.get(rect.size()-1);
					Core.rectangle(original, temp.br(),temp.tl(), Red);
				}
				for(int i = 1; i < contours.size();i++){
					if(Math.abs(rect.get(i).y - rect.get(i-1).y) < 5){
						
						Rect r1 = rect.get(i);
						Rect r2 = rect.get(i-1);
						Point tl = r1.tl();
						Point br = r1.br();
						if(tl.x > r2.tl().x){
							tl.x = r2.tl().x;
						}
						if(tl.y > r2.tl().y){
							tl.y = r2.tl().y;
						}
						if(br.x < r2.br().x){
							br.x = r2.br().x;
						}
						if(br.y < r2.br().y){
							br.y = r2.br().y;
						}
						break;
					}
				}
				table.putBoolean("TargetVisible", true);
			}
			else{
				table.putBoolean("TargetVisible", false);
				for(MatOfPoint mOP : contours){
					Rect rec = Imgproc.boundingRect(mOP);
					Core.rectangle(original, rec.tl(), rec.br(), Red);
				}
				contours.clear();
			}
			if(Process_Gray){
				// Processes image for grey totes
				Core.inRange(grey, grey_Lower, grey_higher, grey);
				Imgproc.findContours(grey, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
				Imgproc.cvtColor(grey, grey, Imgproc.COLOR_GRAY2BGR);
				// Iterates through the list of contours and removes those with an "area" less then 100.
				for (Iterator<MatOfPoint> iterator = contours.iterator(); iterator.hasNext();) {
					MatOfPoint matOfPoint = (MatOfPoint) iterator.next();
					if(matOfPoint.width() * matOfPoint.height() < 100){
						iterator.remove();
					}	
				}
				Rect bb;
				for(MatOfPoint mop : contours){
					bb = Imgproc.boundingRect(mop);
					Core.rectangle(grey, bb.tl(), bb.br(), Red);
				}
			}
			// GUI for the image
			Core.line(original, new Point(frame.width()/2,100),new Point(frame.width()/2,frame.height()-100), Blue);
			Core.line(original, new Point(150,frame.height()/2),new Point(frame.width()-150,frame.height()/2), Blue);
			Core.putText(original, "Team 3019 - Elijah Kaufman", new Point(0,20), 
					Core.FONT_HERSHEY_PLAIN, 1, Green);
			// View this file to see the vision tracking.
			// The window will update after every save; however, it's pretty fast without it.
			Highgui.imwrite("rectangle.png", original);
			// Releases the mats from RAM (free up memory space)
			original.release();
			grey.release();
			frame.release();
		// Mostly for debugging but errors happen
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();	
		}
	}
}