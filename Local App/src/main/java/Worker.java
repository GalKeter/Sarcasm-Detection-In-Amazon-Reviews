import java.util.List;
import software.amazon.awssdk.services.sqs.model.Message;


public class Worker {

    final static AWS aws = AWS.getInstance();
    private static String InQueue = "manager-workers-queue"; 
    private static String OutQueue = "workers-manager-queue";
    private static sentimentAnalysisHandler sentimentHandler = new sentimentAnalysisHandler();
    private static namedEntityRecognitionHandler namedEntityHandler = new namedEntityRecognitionHandler();

    public static void main(String[] args) {
        String InQueueUrl = aws.getQueueUrl(InQueue);
        String OutQueueUrl = aws.getQueueUrl(OutQueue);
        while (true) {
            List<Message> messages = aws.receiveOneMessageFromQueue(InQueueUrl, 300);
            if(messages.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            Message currMessage = messages.get(0);
            String body = currMessage.body();
            String[] split = body.split("\n");
            String[] firstLine = split[0].split(" ");
            String appId = firstLine[0];
            String fileNum = firstLine[1];
            String result = appId + " " + fileNum + "\n";
            for(int i = 1 ; i < split.length ; i++){
                String[] parsedLine = split[i].split("#space#");
                String link = parsedLine[0];
                String review = parsedLine[1];
                String rating = parsedLine[2];
                int sentiment = sentimentHandler.findSentiment(review);
                String entities = namedEntityRecognitionHandler.findEntities(review);
                result += "link: " + link + " rating: " + rating + " sentiment: " + sentiment + " entities: " + entities + "\n";
            }
            aws.sendMessageToQueue(OutQueueUrl, result);
            aws.DeleteOneMessageFromQueue(InQueueUrl, currMessage.receiptHandle());
        }
    }    
}
