package demo;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {

    public static void main(final String[] args) throws Exception {
        final String json = """
                {
                  "author": "Albert Einstein",
                  "quote": "Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning."
                }
                """;

        final ObjectMapper mapper = new ObjectMapper();
        final Quote quote = mapper.readValue(json, Quote.class);
        System.out.println(quote);
    }
}
