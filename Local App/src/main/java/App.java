import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import software.amazon.awssdk.services.sqs.model.Message;
//check
public class App {
    final static AWS aws = AWS.getInstance();
    private static String localApplicationId;
    private static String OutQueueUrl;
    private static String InQueueUrl;
    private static String managerId;
    private static String OutBucketName = "localapps-manager-bucket";
    private static String InBucketName = "manager-localapps-bucket";
    private static String managerScript = "#!/bin/bash\n" +
                                            "yum update -y\n" +
                                            "yum install -y aws-cli\n" +
                                            "aws s3 cp s3://pre-loaded-jars/Manager.jar Manager.jar\n" +
                                            "java -jar Manager.jar >> output.log 2>&1\n" +
                                            "sudo shutdown -h now\n";
    public static void main(String[] args) {// args = [inFilePath, outFilePath, tasksPerWorker, -t (terminate, optional)]
            System.out.println("[DEBUG] local app started");
        try {
            if(args.length < 3){
                throw new RuntimeException("not enough arguments");
            }
            aws.createBucketIfNotExists(OutBucketName);
            aws.createBucketIfNotExists(InBucketName);
            OutQueueUrl = aws.getQueueUrl("localapps-manager-queue");
            if(OutQueueUrl == null)
                OutQueueUrl = aws.createQueue("localapps-manager-queue");
            InQueueUrl = aws.getQueueUrl("manager-localapps-queue");
            if(InQueueUrl == null)
                InQueueUrl = aws.createQueue("manager-localapps-queue");
            //upload jars to s3
            aws.createBucketIfNotExists("pre-loaded-jars");
            aws.uploadFile("pre-loaded-jars", "Manager.jar", "C:\\Users\\Ben\\Downloads\\Assignment 1\\Assignment 1\\Local App\\target\\Manager.jar");
            aws.uploadFile("pre-loaded-jars", "Worker.jar", "C:\\Users\\Ben\\Downloads\\Assignment 1\\Assignment 1\\Local App\\target\\Worker.jar");
            boolean terminate_mode = false;
            Integer ratio;
            String output_file_name;
            if(args[args.length - 1].equals("-t")){
                terminate_mode = true;
                ratio = Integer.parseInt(args[args.length - 2]);
                output_file_name = args[args.length - 3];
            }
            else{
                ratio = Integer.parseInt(args[args.length - 1]);
                output_file_name = args[args.length - 2];
            }  
            localApplicationId = UUID.randomUUID().toString(); //generating a unique id for the local application
            int numOfFiles = 0;
            for(int i=0; args[i] != output_file_name ; i++){ //counting the number of input files
                numOfFiles++;
            }
            int file_num = 1;
            for(int i=0; args[i] != output_file_name ; i++){ //uploading input files to s3
                aws.uploadFile(OutBucketName, localApplicationId + "#" + file_num, "C:\\Users\\Ben\\Downloads\\aws files עבודה 1\\" + args[i]);
                aws.sendMessageToQueue(OutQueueUrl, localApplicationId + " " + file_num + " " + ratio + " " + numOfFiles); // sending messages to the queue, stating the location of the files on S3
                file_num++;
            }
            if(!aws.isManagerActive()){          
                List<String> ids = aws.createEC2(managerScript, "MANAGER", 1); 
                managerId = ids.get(0);
            }
            while(true){ //waiting for the manager to finish processing the files
                List<Message> messages = aws.receiveOneMessageFromQueue(InQueueUrl, 0);
                if(messages.isEmpty()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                Message currMessage = messages.get(0);
                String[] split = currMessage.body().split(" ");
                if(split[0].equals("Done") && split[1].equals(localApplicationId)){
                    aws.DeleteOneMessageFromQueue(InQueueUrl, currMessage.receiptHandle());
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
           
            String output_path ="C:\\Users\\Ben\\Downloads\\aws files עבודה 1\\";
            BufferedReader output = aws.downloadFile(InBucketName, localApplicationId);
            convertTextToColoredHtml(output, output_path + output_file_name +".html");
            if(terminate_mode){
                aws.sendMessageToQueue(OutQueueUrl,  localApplicationId + " " + "TERMINATE" + " " + managerId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void convertTextToColoredHtml(BufferedReader reader, String outputFilePath) {
        try (FileWriter writer = new FileWriter(outputFilePath)) {

            StringBuilder htmlContent = new StringBuilder();
            htmlContent.append("<!DOCTYPE html>\n");
            htmlContent.append("<html lang=\"en\">\n");
            htmlContent.append("<head>\n");
            htmlContent.append("    <meta charset=\"UTF-8\">\n");
            htmlContent.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            htmlContent.append("    <title>Text to HTML Conversion</title>\n");
            htmlContent.append("</head>\n");
            htmlContent.append("<body>\n");

            String line;
            while ((line = reader.readLine()) != null) {
                if(line.length()<8){
                    continue;
                }
                String[] parts = line.split(" ");
                String link = parts[1]; // Extract link from line
                int sentiment = Integer.parseInt(parts[5]); // Extract sentiment from line
                String sentimentColor = getSentimentColor(sentiment);
                String coloredLink = "<a href=\"" + link + "\" style=\"color:" + sentimentColor + ";\">" + link + "</a>";
                String fullSentence = "link: " + coloredLink + " rating: " + parts[3] + " sentiment: " + sentiment+ " entities: " + parts[7];
                htmlContent.append("    ").append(fullSentence).append("<br>\n");
            }

            htmlContent.append("</body>\n");
            htmlContent.append("</html>");

            writer.write(htmlContent.toString());
            reader.close();
            System.out.println("Conversion successful. HTML file saved to: " + outputFilePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }//source: chatGpt

    private static String getSentimentColor(int sentiment) {
        if (sentiment == 1) {
            return "darkred";
        } else if (sentiment == 2) {
            return "red";
        } else if (sentiment == 3) {
            return "black";
        } else if (sentiment == 4) {
            return "lightgreen";
        } else if (sentiment == 5) {
            return "darkgreen";
        } else {
            return "black";
        }
    }//source: chatGpt

}

