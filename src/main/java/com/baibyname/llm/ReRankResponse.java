package com.baibyname.llm;

import java.util.List;

/**
 * Response from the LLM for a re-rank request.
 * Contains the re-ordered list of names with explanations.
 */
public class ReRankResponse {

    private List<RankedName> names;

    public ReRankResponse() {
    }

    public List<RankedName> getNames() {
        return names;
    }

    public void setNames(List<RankedName> names) {
        this.names = names;
    }

    /**
     * A single ranked name with its explanation.
     */
    public static class RankedName {
        private String name;
        private String explanation;

        public RankedName() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }
    }
}
