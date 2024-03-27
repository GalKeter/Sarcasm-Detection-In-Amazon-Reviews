import com.fasterxml.jackson.annotation.JsonProperty;

public class Review {
    private String id;
    private String link;
    private String title;
    private String text;
    private int rating;
    private String author;
    private String date;

    public Review() {
    }

public Review(String id, String link, String title, String text, int rating, String author, String date) {
    this.id = id;
    this.link = link;
    this.title = title;
    this.text = text;
    this.rating = rating;
    this.author = author;
    this.date = date;
}

@JsonProperty("id")
public String getID() {
    return id;
}
@JsonProperty("link")
public String getLink() {
    return link;    
}
@JsonProperty("title")
public String getTitle() {
    return title;
}
@JsonProperty("text")
public String getText() {
    return text;
}
@JsonProperty("rating")
public int getRating() {
    return rating;
}
@JsonProperty("author")
public String getAuthor() {
    return author;
}
@JsonProperty("date")
public String getDate() {
    return date;
}

public void printReview() {
    System.out.println("ID: " + id);
    System.out.println("Link: " + link);
    System.out.println("Title: " + title);
    System.out.println("Text: " + text);
    System.out.println("Rating: " + rating);
    System.out.println("Author: " + author);
    System.out.println("Date: " + date);
    }
}