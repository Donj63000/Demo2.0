package org.example;

import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainSceneSizingTest {

    @Test
    void designSceneSizeUsesReferenceDimensionsOnLargeDisplay() {
        Rectangle2D bounds = new Rectangle2D(0, 0, 2560, 1440);
        Dimension2D size = Main.computeDesignSceneSize(bounds);

        assertEquals(Main.DESIGN_WIDTH, size.getWidth(), 0.01);
        assertEquals(Main.DESIGN_HEIGHT, size.getHeight(), 0.01);
    }

    @Test
    void designSceneSizeFitsInsideSmallerDisplayWithoutOverflow() {
        Rectangle2D bounds = new Rectangle2D(0, 0, 1366, 768);
        Dimension2D size = Main.computeDesignSceneSize(bounds);

        assertTrue(size.getWidth() <= bounds.getWidth() + 0.01);
        assertTrue(size.getHeight() <= bounds.getHeight() + 0.01);
        double expectedAspect = Main.DESIGN_WIDTH / Main.DESIGN_HEIGHT;
        assertEquals(expectedAspect, size.getWidth() / size.getHeight(), 0.01);
    }

    @Test
    void adaptiveSceneSizeUsesConfiguredMarginOnTightDisplays() {
        Rectangle2D bounds = new Rectangle2D(0, 0, 1280, 720);
        Dimension2D size = Main.computeAdaptiveSceneSize(bounds);

        assertEquals(bounds.getWidth() * 0.92, size.getWidth(), 0.01);
        double expectedAspect = Main.DESIGN_WIDTH / Main.DESIGN_HEIGHT;
        assertEquals(expectedAspect, size.getWidth() / size.getHeight(), 0.01);
    }

    @Test
    void adaptiveSceneSizeExpandsToFillVerySmallScreens() {
        Rectangle2D bounds = new Rectangle2D(0, 0, 900, 600);
        Dimension2D size = Main.computeAdaptiveSceneSize(bounds);

        assertEquals(bounds.getWidth(), size.getWidth(), 0.01);
        assertTrue(size.getHeight() <= bounds.getHeight() + 0.01);
    }

    @Test
    void centeredPositionUsesScreenOriginAndSize() {
        Rectangle2D bounds = new Rectangle2D(0, 0, 1920, 1080);
        Dimension2D windowSize = new Dimension2D(1280, 720);

        Point2D position = Main.computeCenteredWindowPosition(bounds, windowSize);

        assertEquals(320.0, position.getX(), 0.01);
        assertEquals(180.0, position.getY(), 0.01);
    }

    @Test
    void centeredPositionClampsWhenWindowExceedsScreen() {
        Rectangle2D bounds = new Rectangle2D(100, 50, 1200, 700);
        Dimension2D windowSize = new Dimension2D(1600, 900);

        Point2D position = Main.computeCenteredWindowPosition(bounds, windowSize);

        assertEquals(bounds.getMinX(), position.getX(), 0.01);
        assertEquals(bounds.getMinY(), position.getY(), 0.01);
    }
}
