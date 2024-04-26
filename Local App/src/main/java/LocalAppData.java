import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LocalAppData {
    private String id;
    private int numOfFiles;
    private HashMap<String, Integer> file2NumOfTasks;
    private HashMap<String, List<String>> file2ProccessedReviews;
    
    public LocalAppData(String id, int numOfFiles)
    {
        this.numOfFiles = numOfFiles;
        this.id = id;
        file2NumOfTasks = new HashMap<String, Integer>();
        file2ProccessedReviews = new HashMap<String, List<String>>();
    }

    public synchronized void updateFile2NumOfTasks(String file, Integer numOfTasks) 
    {
        file2NumOfTasks.put(file, numOfTasks);
        file2ProccessedReviews.put(file, new ArrayList<String>());
    }

    public synchronized void addProccessedReviews(String file, String reviews) 
    {
        file2ProccessedReviews.get(file).add(reviews);
    }

    public synchronized boolean isDone(){
        if(numOfFiles != file2NumOfTasks.size())
        {
            return false;
        }
        for(String key : file2NumOfTasks.keySet())
        {
            if(file2NumOfTasks.get(key) != file2ProccessedReviews.get(key).size())
            {
                return false;
            }
        }
        return true;
    }

    public synchronized String makeSummary(){
        String summary = "";
        for(String key : file2ProccessedReviews.keySet())
        {
            for(String reviews : file2ProccessedReviews.get(key))
            {
                summary += reviews + "\n";
            }
        }
        return summary;
    }
}
