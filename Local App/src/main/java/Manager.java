import software.amazon.awssdk.services.sqs.model.Message;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Manager {
    private static boolean Shouldterminate = false;
    private static  HashMap<String, String> queues;
    private static HashMap<String, LocalAppData> id2LocalAppData; 
    private static ArrayList<String> workersInstances;
    private final static int MAX_WORKERS = 8;
    private static int currentAmountOfWorkers = 0;
    private static String managerId;
    private static String InBucketName = "localapps-manager-bucket";
    private static String OutBucketName = "manager-localapps-bucket";
    private static String workerScript = "#!/bin/bash\n" +
                                        "yum update -y\n" +
                                        "yum install -y aws-cli\n" +
                                        "aws s3 cp s3://pre-loaded-jars/Worker.jar Worker.jar\n" +
                                        "java -Xms2000m -Xmx2500m -jar Worker.jar >> output.log 2>&1\n";
    final static AWS aws = AWS.getInstance();
    private static Object lock = new Object();
    private final static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        initQueues();
        id2LocalAppData = new HashMap<String, LocalAppData>();
        workersInstances = new ArrayList<String>();
            try{
            List<Message> messagesFromApps = new ArrayList<Message>();
            List<Message> messagesFromWorkers = new ArrayList<Message>();
            while(true){
                while (messagesFromApps.isEmpty() && messagesFromWorkers.isEmpty()) { 
                    try{
                        Thread.sleep(3000);
                    }
                    catch(InterruptedException e){
                        e.printStackTrace();
                    }
                    messagesFromApps = aws.receiveOneMessageFromQueue(queues.get("localapps-manager-queue"), 30);
                    messagesFromWorkers = aws.receiveOneMessageFromQueue(queues.get("workers-manager-queue"), 30);
                    if(Shouldterminate){
                        if(_Terminate()){
                            return;
                        }
                    }
                }
                if(!messagesFromApps.isEmpty()){ //handle local apps messages
                    Message curr_message_from_app = messagesFromApps.get(0);
                    messagesFromApps.clear();
                    boolean exists = true;
                    if(!Shouldterminate){
                        aws.DeleteOneMessageFromQueue(queues.get("localapps-manager-queue"), curr_message_from_app.receiptHandle());
                        aws.sendMessageToQueue(queues.get("manager-workers-threads-queue"), curr_message_from_app.body());
                    }
                    else{
                        String body = curr_message_from_app.body();
                        String[] parsed = body.split(" ");
                        String localAppID = parsed[0];
                        exists = false;
                        for(String id : id2LocalAppData.keySet()){
                            if(id.equals(localAppID)){
                                aws.sendMessageToQueue(queues.get("manager-workers-threads-queue"), curr_message_from_app.body());
                                exists = true;
                            }
                        }
                        aws.DeleteOneMessageFromQueue(queues.get("localapps-manager-queue"), curr_message_from_app.receiptHandle());
                    }
                    if(exists){
                        Runnable task = new Runnable(){
                            public void run(){  
                                try{
                                    List<Message> messages = new ArrayList<Message>();
                                    while(messages.isEmpty()){
                                        messages = aws.receiveOneMessageFromQueue(queues.get("manager-workers-threads-queue"), 120);
                                        try{
                                            Thread.sleep(1000);
                                        }
                                        catch(InterruptedException e){
                                            e.printStackTrace();
                                        }
                                    }
                                    Message curr_message = messages.get(0);
                                    messages.clear();
                                    String[] parsed = curr_message.body().split(" ");
                                    int ratio = 0;
                                    int numOfFiles = 0;
                                    String localAppID = "";
                                    String file_num = "";
                                    if(parsed[1].equals("TERMINATE")){
                                        Shouldterminate = true; 
                                        managerId = parsed[2];  
                                        aws.DeleteOneMessageFromQueue(queues.get("manager-workers-threads-queue"), curr_message.receiptHandle());
                                        return;    
                                    }
                                    else{
                                        localAppID = parsed[0];
                                        file_num = parsed[1];
                                        ratio = Integer.parseInt(parsed[2]);
                                        numOfFiles = Integer.parseInt(parsed[3]);
                                    }
                                    BufferedReader inputFile = aws.downloadFile(InBucketName, parsed[0] + "#" + parsed[1]);
                                    String line;
                                    List<Review> reviewsPool = new ArrayList<Review>();
                                    while ((line = inputFile.readLine()) != null) {
                                        ReviewsStructure reviewsStructure = parseReviews(line);
                                        for (Review review : reviewsStructure.getReviews()) {
                                            reviewsPool.add(review);
                                        }
                                    }
                                    inputFile.close();
                                    int poolSize = reviewsPool.size();
                                    System.out.println("debug- size of reviewPool is "+poolSize);
                                    System.out.println("debug- size of ratio is "+ratio);
                                    double double_numOfWorkersNeeded = (double) poolSize / ratio;
                                    int numOfWorkersNeeded =  (int) Math.ceil(double_numOfWorkersNeeded);
                                    synchronized(lock){ //sync workers creation
                                        Thread.sleep(5000);
                                        currentAmountOfWorkers = aws.getActiveWorkerCount();
                                        System.out.println("debug- size of currentAmountOfWorkers is "+currentAmountOfWorkers);
                                        int workersToCreate = Math.max(0 , numOfWorkersNeeded - currentAmountOfWorkers);
                                        if(workersToCreate > 0){
                                            workersToCreate = Math.min(MAX_WORKERS - currentAmountOfWorkers, workersToCreate);
                                            if (workersToCreate > 0) {
                                                List<String> instances = aws.createEC2(workerScript, "WORKER", workersToCreate);
                                                for(String instance : instances){
                                                    workersInstances.add(instance);
                                                    }
                                                }
                                            }
                                    }
                                    int index = 0;
                                    String message="";
                                    int numOftasks = 0;
                                    for(Review review : reviewsPool){
                                        if(index == 0){
                                            message += localAppID + " " + file_num + "\n";
                                        }
                                        message += review.getLink() + "#space#" + review.getText() + "#space#" + review.getRating() + "\n";
                                        index++;
                                        if(index==ratio){
                                            aws.sendMessageToQueue(queues.get("manager-workers-queue"), message);
                                            index = 0;
                                            message = "";
                                            numOftasks++;
                                        }
                                    }
                                    if(message!=""){ //send the rest of the messages
                                        aws.sendMessageToQueue(queues.get("manager-workers-queue"), message);
                                        numOftasks++;
                                    }
                                    if( id2LocalAppData.get(localAppID)==null){
                                        id2LocalAppData.put(localAppID, new LocalAppData(localAppID, numOfFiles));
                                    }
                                    id2LocalAppData.get(localAppID).updateFile2NumOfTasks(file_num, numOftasks);
                                    reviewsPool.clear();
                                    aws.DeleteOneMessageFromQueue(queues.get("manager-workers-threads-queue"), curr_message.receiptHandle());

                                }
                                catch(Exception e){
                                    e.printStackTrace();
                                    }                
                                }
                        };
                        executor.execute(task);
                }
            }
                if(!messagesFromWorkers.isEmpty()){ //handle messages from workers
                    Message curr_message_from_worker = messagesFromWorkers.get(0);
                    aws.DeleteOneMessageFromQueue(queues.get("workers-manager-queue"), curr_message_from_worker.receiptHandle());
                    aws.sendMessageToQueue(queues.get("workers-manager-threads-queue"), curr_message_from_worker.body());
                    messagesFromWorkers.clear();
                    Runnable task = new Runnable(){
                        public void run(){  
                            List<Message> messages = new ArrayList<Message>();
                            while(messages.isEmpty()){
                                messages = aws.receiveOneMessageFromQueue(queues.get("workers-manager-threads-queue"),120);
                                try{
                                    Thread.sleep(1000);
                                }
                                catch(InterruptedException e){
                                    e.printStackTrace();
                                }
                            }
                            Message curr_message_from_worker = messages.get(0);
                            messages.clear();
                            String message = curr_message_from_worker.body();
                            String[] parsed = message.split("\n");
                            String firstLine = parsed[0];
                            String[] parsedFirstLine = firstLine.split(" ");
                            String localAppID = parsedFirstLine[0];
                            String file_num = parsedFirstLine[1];
                            String processedReviews = message.substring(firstLine.length() + 1);
                            id2LocalAppData.get(localAppID).addProccessedReviews(file_num, processedReviews);
                            if(id2LocalAppData.get(localAppID).isDone()){
                                String summary = id2LocalAppData.get(localAppID).makeSummary();
                                ByteArrayInputStream  toSend = new ByteArrayInputStream(summary.getBytes(StandardCharsets.UTF_8));
                                aws.uploadFile_fromBuffer(OutBucketName, localAppID, toSend);
                                aws.sendMessageToQueue(queues.get("manager-localapps-queue"),"Done" + " " + localAppID);
                                id2LocalAppData.remove(localAppID);
                                }
                            aws.DeleteOneMessageFromQueue(queues.get("workers-manager-threads-queue"), curr_message_from_worker.receiptHandle());
                            }
                    };
                    executor.execute(task);
                }
            }
        }
    catch(Exception e){
        e.printStackTrace();
    }
}         

    private static void initQueues(){
        queues = new HashMap<String, String>();
        String url = aws.getQueueUrl("localapps-manager-queue");
        if(url != null)
            queues.put("localapps-manager-queue", url);
        else{
            queues.put("localapps-manager-queue", aws.createQueue("localapps-manager-queue"));
        }
        url = aws.getQueueUrl("manager-localapps-queue");
        if(url != null)
            queues.put("manager-localapps-queue", url);
        else{  
            queues.put("manager-localapps-queue", aws.createQueue("manager-localapps-queue"));
        }
        url = aws.getQueueUrl("manager-workers-queue");
        if(url != null)
            queues.put("manager-workers-queue", url);
        else{  
            queues.put("manager-workers-queue", aws.createQueue("manager-workers-queue"));
        }
        url = aws.getQueueUrl("workers-manager-queue");
        if(url != null)
            queues.put("workers-manager-queue", url);
        else{  
            queues.put("workers-manager-queue", aws.createQueue("workers-manager-queue"));
        }
        url = aws.getQueueUrl("workers-manager-threads-queue");
        if(url != null)
            queues.put("workers-manager-threads-queue", url);
        else{  
            queues.put("workers-manager-threads-queue", aws.createQueue("workers-manager-threads-queue"));
        }
        url = aws.getQueueUrl("manager-workers-threads-queue");
        if(url != null)
            queues.put("manager-workers-threads-queue", url);
        else{  
            queues.put("manager-workers-threads-queue", aws.createQueue("manager-workers-threads-queue"));
        }
    }

    private static ReviewsStructure parseReviews(String input){
        ObjectMapper mapper = new ObjectMapper();
        ReviewsStructure reviewsStructure = null;
        try {
            reviewsStructure = mapper.readValue(input, ReviewsStructure.class);
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return reviewsStructure;
    }

    private static boolean _Terminate(){
        if(id2LocalAppData.isEmpty()){
            try{
                for(String instance : workersInstances){ //terminate all workers
                    aws.terminateEC2(instance);
                }
                aws.deleteBucketWithObjects(InBucketName);
                aws.deleteBucketWithObjects("pre-loaded-jars");
                aws.deleteQueue(queues.get("localapps-manager-queue"));
                aws.deleteQueue(queues.get("manager-workers-queue"));
                aws.deleteQueue(queues.get("workers-manager-queue"));
                aws.deleteQueue(queues.get("workers-manager-threads-queue"));
                aws.deleteQueue(queues.get("manager-workers-threads-queue"));
                executor.shutdown();
                aws.close();
                return true;
            }
            catch(Exception e){
                e.printStackTrace();
                return false;
            }
        }
        else{
            return false;
        }
    }
}

        
