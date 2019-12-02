package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.util.*;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;

public class TextAssessmentUtilityService {

    private static FeedbackService feedbackService;

    public TextAssessmentUtilityService(FeedbackService feedbackService) {
        TextAssessmentUtilityService.feedbackService = feedbackService;
    }

    /**
     * Calculates the variance of a given text textCluster if the number of assessed text block in the textCluster exceeds the variance thresehold
     *
     * @param textCluster
     * @return
     */
    public static Optional<Double> calculateVariance(TextCluster textCluster, int thresholdSize) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        // If not enough text blocks in a textCluster are assessed, return an empty optional
        if (allAssessedBlocks.size() < thresholdSize) {
            return Optional.empty();
        }

        // Expected value of a textCluster
        final double expectedValue = calculateExpectation(textCluster).get();

        return Optional.of(allAssessedBlocks.stream()

                // Calculate the variance of each random variable
                .mapToDouble(block -> {
                    if (getCreditsOfTextBlock(block).isPresent() && calculateScoreCoveragePercentage(block).isPresent()) {
                        return (getCreditsOfTextBlock(block).get() - expectedValue) * calculateScoreCoveragePercentage(block).get();
                    }
                    return 0.0;
                })
                // Sum them up to get the final value
                .reduce(0.0, Double::sum));
    }

    /**
     * Calculates the expected value in a text textCluster
     *
     * @param textCluster Text textCluster for which the expected value is calculated
     * @return
     */
    public static Optional<Double> calculateExpectation(TextCluster textCluster) {
        return Optional.of(textCluster.getBlocks().stream()

                // Only take the text blocks which are credited
                .filter(block -> getCreditsOfTextBlock(block).isPresent())

                // Calculate the expected value of each random variable (the credit score) and its
                // probability (its coverage percentage over a textCluster which is uniformly distributed)
                .mapToDouble(block -> (double) (1 / textCluster.size()) * getCreditsOfTextBlock(block).get())

                // Sum the up to create the expectation value of a textCluster in terms of a score
                .sum());
    }

    /**
     * Calculates the standard deviation of a text textCluster
     *
     * @param textCluster textCluster for which the standard deviation is calculated
     * @return {Optional<Double>} standard deviation of a text cluster
     */
    public static Optional<Double> calculateStandardDeviation(TextCluster textCluster, int thresholdSize) {

        final double variance = calculateVariance(textCluster, thresholdSize).get();

        return Optional.of(Math.sqrt(variance));
    }

    /**
     * Calculates the coverage percentage of how  many text blocks in a textCluster are present
     *
     * @param textCluster textCluster for which the coverage percentage is calculated
     * @return
     */
    public static Optional<Double> calculateCoveragePercentage(TextCluster textCluster) {
        if (textCluster != null) {
            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final double creditedBlocks = (double) allBlocksInCluster.stream().filter(block -> (getCreditsOfTextBlock(block).isPresent())).count();

            return Optional.of(creditedBlocks / (double) allBlocksInCluster.size());
        }

        return Optional.empty();
    }

    /**
     * Calculates the coverage percentage of a textCluster with the same score as a given text block
     *
     * @param textBlock text block for which its textClusters score coverage percentage is determined
     * @return {Optional<Double>} Optional which contains the percentage between [0.0-1.0] or empty if text block is not in a textCluster
     */
    public static Optional<Double> calculateScoreCoveragePercentage(TextBlock textBlock) {
        final TextCluster textCluster = textBlock.getCluster();

        if (textCluster != null) {

            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final double textBlockCredits = getCreditsOfTextBlock(textBlock).get();

            final List<TextBlock> blocksWithSameCredit = textCluster.getBlocks()

                    .parallelStream()

                    // Get all text blocks which have the same score as the current text block
                    .filter(block -> (getCreditsOfTextBlock(block).get() == textBlockCredits))

                    .collect(toList());

            return Optional.of(((double) blocksWithSameCredit.size()) / ((double) allBlocksInCluster.size()));
        }

        return Optional.empty();
    }

    /**
     * Calculates the average score of a text textCluster
     *
     * @param textCluster text textCluster for which the average score should be computed
     * @return {Optional<Double>} Optional which contains the average scores or empty if there are none
     */
    public static Optional<Double> calculateAverage(TextCluster textCluster) {
        if (textCluster != null) {

            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final Double scoringSum = allBlocksInCluster.stream().mapToDouble(block -> {
                if (getCreditsOfTextBlock((block)).isPresent()) {
                    return getCreditsOfTextBlock(block).get();
                }
                return 0.0;
            }).sum();

            return Optional.of(scoringSum / (double) allBlocksInCluster.size());
        }

        return Optional.empty();
    }

    /**
     * Gets the max score of a text textCluster
     * @param textCluster
     * @return
     */
    public static OptionalDouble getMaxScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        if (allAssessedBlocks.size() > 0) {
            return allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).get()).reduce(Math::max);
        }

        return OptionalDouble.empty();
    }

    /**
     * Gets the median score of a textCluster
     * @param textCluster
     * @return
     */
    public static Optional<Double> getMedianScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        final double[] textBlockArray = allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).get()).toArray();

        Arrays.sort(textBlockArray);

        if (textBlockArray.length > 0) {
            return Optional.of(textBlockArray[textBlockArray.length / 2]);
        }
        return Optional.empty();
    }

    /**
     * Gets the minimum score of a text textCluster
     * @param textCluster
     * @return
     */
    public static OptionalDouble getMinimumScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        if (allAssessedBlocks.size() > 0) {
            return allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).get()).reduce(Math::min);
        }
        return OptionalDouble.empty();
    }

    /**
     * Getter for the textCluster size of a text textCluster
     * @param textCluster textCluster for which the size should be returned
     * @return {Integer} size of the textCluster
     */
    public static Integer getClusterSize(TextCluster textCluster) {
        return textCluster.size();
    }

    /**
     * Returns the size of the textCluster of a text block
     * @param textBlock textBlock for which the textCluster size should be determined
     * @return {Integer} size of the text textCluster
     */
    public static Integer getClusterSize(TextBlock textBlock) {
        return textBlock.getCluster().size();
    }

    /**
     * Returns the credits of a text block as an optional depending on wether a text block has credits
     * @param block {TextBlock} text block for which the credits are returned
     * @return {Optional<Double>} credits of the text block as Double Object in an Optional Wrapper
     */
    public static Optional<Double> getCreditsOfTextBlock(TextBlock block) {
        final TextCluster textCluster = block.getCluster();

        final Map<String, Feedback> feedback = feedbackService.getFeedbackForTextExerciseInCluster(textCluster);
        if (feedback != null) {
            return Optional.of(feedback.get(block.getId()).getCredits());
        }
        return Optional.empty();
    }

    /**
     * Returns all assessed block in a text textCluster as a List
     * @param textCluster textCluster for which all assessed blocks should be returned
     * @return {List<Textblock>} all assessed text blocks
     */
    public static List<TextBlock> getAssessedBlocks(TextCluster textCluster) {
        return textCluster.getBlocks()

                .stream()
                // Only get text blocks which are assessed
                .filter(block -> getCreditsOfTextBlock(block).isPresent())

                .collect(toList());
    }

    /**
     * Calculates a suggested feedback score for a textblock based on its surrounding neighbors in a Text cluster
     * @param textBlock
     * @return
     */
    public static OptionalDouble calculateScore(TextBlock textBlock) {
        final TextCluster textCluster = textBlock.getCluster();

        final List<TextBlock> assessedBlocks = getAssessedBlocks(textCluster);

        if (getCreditsOfTextBlock(textBlock).isPresent() || assessedBlocks.size() < 1) {
            return OptionalDouble.empty();
        }

        // Get the total distance between the text block which is automatically assessed and every other assessed block in the cluster
        final double totalWeight = assessedBlocks.stream()

                // Get the distance between the original text block and each assessed block
                .mapToDouble(blockIterator -> (1 / Math.abs(textCluster.distanceBetweenBlocks(textBlock, blockIterator))))
                // Sum the up to get the total distance
                .reduce(Double::sum)
                // Return as double
                .getAsDouble();

        final double weightedScore = assessedBlocks.stream()

                // Calculate the impact each assessed block should have on the final score, by determining the percental closeness of its weight compared to the other assessed
                // blocks
                .mapToDouble(
                        blockIterator -> ((1 / Math.abs(textCluster.distanceBetweenBlocks(textBlock, blockIterator))) / totalWeight) * getCreditsOfTextBlock(blockIterator).get())

                .sum();

        // Get upper range threshold for wether the score matches the expectation
        final OptionalDouble upperRange = OptionalDouble.of(calculateExpectation(textCluster).get() + calculateStandardDeviation(textCluster, 5).get());

        // Get lower range threshold for wether the score matches the expectation
        final OptionalDouble lowerRange = OptionalDouble.of(calculateExpectation(textCluster).get() - calculateStandardDeviation(textCluster, 5).get());

        // Verify if weighted score lies in range interval
        if (weightedScore < upperRange.getAsDouble() && weightedScore > lowerRange.getAsDouble()) {
            return OptionalDouble.of(weightedScore);
        }
        return OptionalDouble.empty();
    }

}
