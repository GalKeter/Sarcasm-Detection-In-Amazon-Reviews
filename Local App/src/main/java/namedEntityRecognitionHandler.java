import java.util.Properties;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import java.util.List;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;

import edu.stanford.nlp.util.CoreMap;

public class namedEntityRecognitionHandler {
    Properties properties;
    static StanfordCoreNLP NERPipeline;

    public namedEntityRecognitionHandler() {
        properties = new Properties();
        properties.put("annotators", "tokenize , ssplit, pos, lemma, ner");
        NERPipeline = new StanfordCoreNLP(properties);   
    }
    public static String findEntities(String review){
        Annotation document = new Annotation(review);
        NERPipeline.annotate(document);
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        String result = "[";
        for(CoreMap sentence: sentences) {
            for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
                String ne = token.get(NamedEntityTagAnnotation.class);
                if (ne.equals("PERSON") || ne.equals("LOCATION") || ne.equals("ORGANIZATION")) {
                    String word = token.get(TextAnnotation.class);
                    result += word + ":" + ne + ",";
                    }
                }
            }
        if(result.length()==1){
            return "[]";
        }
        result = result.substring(0, result.length() - 1);
        result += "]";
        return result;
    }
}
