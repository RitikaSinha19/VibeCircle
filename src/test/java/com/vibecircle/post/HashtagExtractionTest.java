package com.vibecircle.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class HashtagExtractionTest {

    @Test
    void lowercasesDeduplicatesAndKeepsFirstSeenOrder() {
        Set<String> tags = PostService.extractHashtags("Great #Cricket day #cricket #Code_1 #AI");
        assertThat(tags).containsExactly("cricket", "code_1", "ai");
    }

    @Test
    void ignoresHashesInsideWordsAndLoneHashes() {
        assertThat(PostService.extractHashtags("issue#42 and C# and # alone and a#b")).isEmpty();
    }

    @Test
    void handlesNullAndEmptyCaptions() {
        assertThat(PostService.extractHashtags(null)).isEmpty();
        assertThat(PostService.extractHashtags("")).isEmpty();
        assertThat(PostService.extractHashtags("no tags here")).isEmpty();
    }

    @Test
    void stopsAtTheLimit() {
        StringBuilder caption = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            caption.append("#tag").append(i).append(' ');
        }
        assertThat(PostService.extractHashtags(caption.toString())).hasSize(PostService.MAX_HASHTAGS);
    }
}
