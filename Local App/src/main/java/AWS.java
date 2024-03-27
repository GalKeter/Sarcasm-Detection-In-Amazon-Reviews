
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;

public class AWS {
    private final S3Client s3;
    private final SqsClient sqs;
    private final Ec2Client ec2;

    public static Region region1 = Region.US_EAST_1;
    public static Region region2 = Region.US_WEST_2;
    public static String amiId = "ami-00e95a9222311e8ed";

    private static final AWS instance = new AWS();

    private AWS() {
        s3 = S3Client.builder().region(region2).build();
        sqs = SqsClient.builder().region(region2).build();
        ec2 = Ec2Client.builder().region(region1).build();
    }

    public static AWS getInstance() {
        return instance;
    }

    // S3
    public void createBucketIfNotExists(String bucketName) {
        try {
            s3.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(BucketLocationConstraint.US_WEST_2)
                                    .build())
                    .build());
            s3.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (S3Exception e) {
            System.out.println(e.getMessage());
        }
    }
    
    public void deleteBucketWithObjects(String bucketName) {
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder().bucket(bucketName).build();
        ListObjectsResponse listObjectsResponse = s3.listObjects(listObjectsRequest);
        try{
        for (S3Object s3Object : listObjectsResponse.contents()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build());
        }
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
        System.out.println("[DEBUG] Bucket deleted successfully!");}
        catch (S3Exception e) {
            System.out.println(e.getMessage());
        } 
    }

    public void uploadFile(String bucketName, String key, String filePath) {
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build(),
                    RequestBody.fromFile(new File(filePath)));
        } catch (S3Exception e) {
            System.out.println(e.getMessage());
        }
        System.out.println("[DEBUG] File uploaded successfully!");
    }

    public void uploadFile_fromBuffer(String bucketName, String key, ByteArrayInputStream inputStream) {
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build(),
                    RequestBody.fromInputStream(inputStream, inputStream.available()));
        } catch (S3Exception e) {
            System.out.println(e.getMessage());
        }
        System.out.println("[DEBUG] File uploaded successfully!");
    }

    public void downloadFile(String bucketName, String key, String filePath) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try {
            GetObjectResponse getObjectResponse = s3.getObject(getObjectRequest,
                    ResponseTransformer.toFile(Paths.get(filePath)));
            System.out.println("[DEBUG] File downloaded successfully!");
        } catch (S3Exception e) {
            System.err.println("Error downloading file from S3: " + e.getMessage());
        }
    }

    public BufferedReader downloadFile(String bucketName, String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try {
            ResponseInputStream<GetObjectResponse> inputStream = s3.getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            System.out.println("[DEBUG] File downloaded successfully!");
            return reader;
        } catch (Exception e) {
            System.err.println("Error downloading/reading file from S3: " + e.getMessage());
            throw e;
        }
    }


    // SQS
    public String createQueue(String queueName) {
        try {
            CreateQueueRequest req = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build();
            CreateQueueResponse create_result = sqs.createQueue(req);
            String queueUrl = create_result.queueUrl();
            System.out.println("[DEBUG] Queue created successfully! Queue URL: " + queueUrl);
            return queueUrl;
        } catch (SqsException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }    
    
    public void deleteQueue(String queueUrl) {
        try{
            DeleteQueueRequest request = DeleteQueueRequest.builder().queueUrl(queueUrl).build();
            sqs.deleteQueue(request);
        }
        catch (SqsException e) {
            System.out.println(e.getMessage());
        }
    }
    
    public String getQueueUrl(String queueName) {
        try {
            return sqs.getQueueUrl(r -> r.queueName(queueName)).queueUrl();
        } catch (QueueDoesNotExistException e) {
            //System.out.println(e.getMessage());
            return null;
        }
    }

    public void printQueueList() {
        try {
            ListQueuesResponse listQueuesResponse = sqs.listQueues();
            for (String url : listQueuesResponse.queueUrls()) {
                System.out.println(url);
            }
        } catch (SqsException e) {
            System.out.println(e.getMessage());
        }
    }

    public void sendMessageToQueue(String queue_url, String message){
        try {
            SendMessageRequest send_msg_request = SendMessageRequest.builder()
            .queueUrl(queue_url)
            .messageBody(message)
            .delaySeconds(1)
            .build();
            sqs.sendMessage(send_msg_request);
        } catch (SqsException e) {
            System.out.println(e.getMessage());
        }
  
  }

  public List<Message> receiveMessagesFromQueue(String queue_url){
    try {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
        .queueUrl(queue_url)
        .build();
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();
        return messages;
    } catch (SqsException e) {
        System.out.println(e.getMessage());
        throw e;
        //return null;
    }
  
    }

    
  public List<Message> receiveOneMessageFromQueue(String queue_url, int visibilityTimeout){
    try {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
        .queueUrl(queue_url)
        .maxNumberOfMessages(1)
        .visibilityTimeout(visibilityTimeout)
        .build();
        List<Message> temp = sqs.receiveMessage(receiveRequest).messages();
        List<Message> messages = new ArrayList<>(); //to avoid unmodifiable list
        if(temp.size() > 0){
            messages.add(temp.get(0));
        }
        return messages;
    } catch (SqsException e) {
        System.out.println(e.getMessage());
        throw e;
        //return null;
    }
  
    }

    public void DeleteMessagesFromQueue(String queueUrl) {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .build();
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();
        for (Message m : messages) {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(m.receiptHandle())
                    .build();
                sqs.deleteMessage(deleteRequest);
                System.out.println("i deleted bro");
            }
    }

    public void DeleteOneMessageFromQueue(String queueUrl, String receiptHandle) { 
        try{
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();
                sqs.deleteMessage(deleteRequest);
        }
        catch(SqsException e){
            System.out.println(e.getMessage());
        }
    }
            
    

//EC2

    public boolean isManagerActive(){
    DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
    DescribeInstancesResponse response = ec2.describeInstances(request);
    for (Reservation reservation : response.reservations()) {
        for (Instance instance : reservation.instances()) {
            for(Tag tag : instance.tags()){
                if((instance.state().name()==InstanceStateName.RUNNING || instance.state().name()==InstanceStateName.PENDING) && tag.key().equals("Name") && tag.value().equals("MANAGER")){
                    return true;
                }
            }
        }
    }
    return false;
}

    public List<String> createEC2(String script, String tagName, int numberOfInstances) {
        RunInstancesRequest runRequest = (RunInstancesRequest) RunInstancesRequest.builder()
                .instanceType(InstanceType.T2_MEDIUM)
                .imageId(amiId)
                .maxCount(numberOfInstances)
                .minCount(1)
                .keyName("vockey")
                .securityGroupIds("sg-0b9259feddb32cb76")
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .userData(Base64.getEncoder().encodeToString((script).getBytes()))
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        List<String> instanceIds = new ArrayList<>();
        for(Instance instance : response.instances()){
            String instanceId = instance.instanceId();
            instanceIds.add(instanceId);
            Tag tag = Tag.builder()
                    .key("Name")
                    .value(tagName)
                    .build();
            CreateTagsRequest tagRequest = (CreateTagsRequest) CreateTagsRequest.builder()
                    .resources(instanceId)
                    .tags(tag)
                    .build();
            try {
                ec2.createTags(tagRequest);
                System.out.printf(
                        "[DEBUG] Successfully started EC2 instance %s based on AMI %s\n",
                        instanceId, amiId);
            } catch (Ec2Exception e) {
                System.err.println("[ERROR] " + e.getMessage());
                System.exit(1);
            }
        }
        return instanceIds;
    }

    public void terminateEC2(String instanceId) {
        try {
            TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
            ec2.terminateInstances(terminateRequest);
            System.out.println("[DEBUG] Successfully terminated EC2 instance " + instanceId);
        } catch (Ec2Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }

    public int getActiveWorkerCount() {
        int counter = 0;
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        DescribeInstancesResponse response = ec2.describeInstances(request);
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                for(Tag tag : instance.tags()){
                    if((instance.state().name()==InstanceStateName.RUNNING || instance.state().name()==InstanceStateName.PENDING) && tag.key().equals("Name") && tag.value().equals("WORKER")){
                        counter++; 
                    }
                }
            }
        }
        return counter;
    }  


    //close
    public void close() {
        s3.close();
        sqs.close();
        ec2.close();
    }


}