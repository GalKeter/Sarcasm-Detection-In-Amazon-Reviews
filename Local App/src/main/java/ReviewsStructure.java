import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReviewsStructure {
    private String title;
    private List<Review> reviews;

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    @JsonProperty("reviews")
    public List<Review> getReviews() {
        return reviews;
    }
}