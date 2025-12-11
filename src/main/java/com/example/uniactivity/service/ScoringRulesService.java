package com.example.uniactivity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class ScoringRulesService {
    
    private final ObjectMapper objectMapper;
    
    @Getter
    private JsonNode scoringRules;
    
    public ScoringRulesService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void loadRules() {
        try {
            ClassPathResource resource = new ClassPathResource("scoring-rules.json");
            InputStream is = resource.getInputStream();
            this.scoringRules = objectMapper.readTree(is);
            log.info("Loaded scoring rules with {} categories", 
                scoringRules.get("categories").size());
        } catch (IOException e) {
            log.error("Failed to load scoring rules", e);
            throw new RuntimeException("Could not load scoring-rules.json", e);
        }
    }
    
    /**
     * Calculate academic score (1.1) based on current and previous GPA
     */
    public int calculateAcademicScore(double currentGpa, double previousGpa) {
        int baseScore = calculateBaseScore(currentGpa);
        int adjustScore = calculateGpaChangeScore(currentGpa, previousGpa);
        return baseScore + adjustScore;
    }
    
    /**
     * Get base score from current GPA
     */
    private int calculateBaseScore(double gpa) {
        JsonNode baseScoreRules = scoringRules.get("gpaRules").get("baseScore");
        
        for (JsonNode rule : baseScoreRules) {
            if (gpa < rule.get("maxGpa").asDouble()) {
                return rule.get("score").asInt();
            }
        }
        return 12; // Max score for GPA 8.0-10.0
    }
    
    /**
     * Calculate bonus/penalty from GPA change
     */
    private int calculateGpaChangeScore(double currentGpa, double previousGpa) {
        double diff = currentGpa - previousGpa;
        
        if (Math.abs(diff) < 0.01) {
            return 0; // No change
        }
        
        if (diff > 0) {
            // GPA increased - bonus
            JsonNode bonusRules = scoringRules.get("gpaRules").get("increaseBonus");
            for (JsonNode rule : bonusRules) {
                if (diff < rule.get("maxDiff").asDouble()) {
                    return rule.get("score").asInt();
                }
            }
            return 10; // Max bonus
        } else {
            // GPA decreased - penalty
            double absDiff = Math.abs(diff);
            JsonNode penaltyRules = scoringRules.get("gpaRules").get("decreasePenalty");
            for (JsonNode rule : penaltyRules) {
                if (absDiff < rule.get("maxDiff").asDouble()) {
                    return rule.get("score").asInt();
                }
            }
            return -10; // Max penalty
        }
    }
    
    /**
     * Get classification label based on total score
     */
    public String getClassification(int totalScore) {
        JsonNode classifications = scoringRules.get("classification");
        for (JsonNode classification : classifications) {
            int min = classification.get("min").asInt();
            int max = classification.get("max").asInt();
            if (totalScore >= min && totalScore <= max) {
                return classification.get("label").asText();
            }
        }
        return "Kém";
    }
    
    /**
     * Get CSS class for classification badge
     */
    public String getClassificationCss(int totalScore) {
        JsonNode classifications = scoringRules.get("classification");
        for (JsonNode classification : classifications) {
            int min = classification.get("min").asInt();
            int max = classification.get("max").asInt();
            if (totalScore >= min && totalScore <= max) {
                return classification.get("cssClass").asText();
            }
        }
        return "dark";
    }
    
    /**
     * Normalize total score to 0-100 range
     */
    public int normalizeScore(int rawScore) {
        if (rawScore > 100) return 100;
        if (rawScore < 0) return 0;
        return rawScore;
    }
    
    /**
     * Get rules HTML for a specific category code
     */
    public String getRulesHtml(String categoryCode) {
        JsonNode categories = scoringRules.get("categories");
        for (JsonNode category : categories) {
            JsonNode subcategories = category.get("subcategories");
            for (JsonNode subcategory : subcategories) {
                if (subcategory.get("id").asText().equals(categoryCode)) {
                    return subcategory.has("rulesHtml") ? 
                        subcategory.get("rulesHtml").asText() : "";
                }
            }
        }
        return "";
    }
    
    /**
     * Get default score for a category
     */
    public int getDefaultScore(String categoryCode) {
        JsonNode categories = scoringRules.get("categories");
        for (JsonNode category : categories) {
            JsonNode subcategories = category.get("subcategories");
            for (JsonNode subcategory : subcategories) {
                if (subcategory.get("id").asText().equals(categoryCode)) {
                    return subcategory.has("defaultScore") ? 
                        subcategory.get("defaultScore").asInt() : 0;
                }
            }
        }
        return 0;
    }
    
    /**
     * Check if category requires evidence
     */
    public boolean requiresEvidence(String categoryCode) {
        JsonNode categories = scoringRules.get("categories");
        for (JsonNode category : categories) {
            JsonNode subcategories = category.get("subcategories");
            for (JsonNode subcategory : subcategories) {
                if (subcategory.get("id").asText().equals(categoryCode)) {
                    return subcategory.has("evidenceRequired") && 
                        subcategory.get("evidenceRequired").asBoolean();
                }
            }
        }
        return false;
    }
    
    /**
     * Check if criteria code is valid (exists in scoring rules)
     */
    public boolean isValidCriteriaCode(String criteriaCode) {
        if (criteriaCode == null || criteriaCode.isEmpty()) {
            return false;
        }
        JsonNode categories = scoringRules.get("categories");
        for (JsonNode category : categories) {
            JsonNode subcategories = category.get("subcategories");
            for (JsonNode subcategory : subcategories) {
                if (subcategory.get("id").asText().equals(criteriaCode)) {
                    return true;
                }
            }
        }
        return false;
    }
}
