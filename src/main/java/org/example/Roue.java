package org.example;

import javafx.animation.*;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.*;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class Roue {

    private static final double HUB_RADIUS      = Main.WHEEL_RADIUS * .28;
    private static final Color  HUB_STROKE      = Theme.ACCENT;
    private static final double HUB_STROKE_W    = 3;
    private static final Color  SECTOR_BORDER   = Color.rgb(0,0,0,.25);
    private static final double SECTOR_BORDER_W = 1.1;
    private static final double GOLDEN_ANGLE    = 137.50776405003785;
    private static final double BASE_ANGULAR_SPEED = 360.0; // deg/s for constant perceived speed

    private static Color colorByIndex(int idx){
        double h = (idx * GOLDEN_ANGLE) % 360;
        return Color.hsb(h, .85, .90);
    }

    private final StackPane root;
    private final Group     wheelGroup;
    private final RotateTransition rot;
    private final Resultat  resultat;
    private final List<Arc> arcs = new ArrayList<>();
    private MediaPlayer spinPlayer;
    private boolean spinSoundFailed;

    private String[] seatNames;
    private Color[]  seatColors;

    private Timeline rainbowLoop;
    private Consumer<String>   spinCallback;

    private double dragX, dragY;

    public Roue(Resultat res){
        this.resultat = res;
        this.rot = new RotateTransition();

        root = new StackPane();
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(Main.WHEEL_RADIUS*2, Main.WHEEL_RADIUS*2);

        wheelGroup = new Group();
        wheelGroup.setCache(true);
        wheelGroup.setCacheHint(CacheHint.ROTATE);
        root.getChildren().add(wheelGroup);

        enableDrag();
    }

    public Node getRootPane(){ return root; }
    public void resetPosition(){ root.setTranslateX(0); root.setTranslateY(0); }
    public void setOnSpinFinished(Consumer<String> cb){ spinCallback = cb; }

    public void updateWheelDisplay(ObservableList<String> tickets){
        buildSeatArrays(tickets, OptionRoue.getLosingTickets());

        wheelGroup.setRotate(0);
        rot.stop();
        stopSpinSound();
        wheelGroup.getChildren().clear();
        arcs.clear();

        addDecorRings();

        double step = 360d/ seatNames.length, start=0;
        for(int i=0;i<seatNames.length;i++){
            Arc a = buildSector(start, step, seatColors[i], seatNames[i]==null);
            arcs.add(a);
            wheelGroup.getChildren().add(a);
            start += step;
        }
        wheelGroup.getChildren().add(buildGlossOverlay());
        wheelGroup.getChildren().add(buildHub());
    }

    public void spinTheWheel(ObservableList<String> t){ updateWheelDisplay(t); spinTheWheel(); }
    public void spinTheWheel(){
        stopHighlight();

        int total = seatNames==null?0:seatNames.length;
        if(total==0){ resultat.setMessage("Aucun ticket – impossible de lancer la roue."); return; }

        int idx = ThreadLocalRandom.current().nextInt(total);
        double step = 360d/total;
        double offset = idx * step + step / 2 - 90;

        double duration = OptionRoue.getSpinDuration();
        double travel = BASE_ANGULAR_SPEED * duration;
        double end = travel + offset;

        rot.setDuration(Duration.seconds(duration));
        rot.setNode(wheelGroup);
        rot.setFromAngle(wheelGroup.getRotate());
        rot.setToAngle(wheelGroup.getRotate() + end);
        rot.setInterpolator(Interpolator.EASE_OUT);
        rot.setOnFinished(e -> {
            stopSpinSound();
            String pseudo = seatNames[idx];
            resultat.setMessage(pseudo!=null ? pseudo+" a gagné !" : "Perdu !");
            if(spinCallback!=null) spinCallback.accept(pseudo);
            highlightWinner(idx);
        });
        startSpinSound();
        rot.play();
    }

    private void highlightWinner(int idx){
        if(idx<0||idx>=arcs.size()) return;
        Arc a = arcs.get(idx);

        a.setStrokeWidth(SECTOR_BORDER_W*2);
        a.setEffect(new Glow(.8));

        rainbowLoop = new Timeline();
        double durationS = 1.5;
        int    steps     = 20;
        for(int i=0;i<=steps;i++){
            double frac = (double)i/steps;
            Color col = Color.hsb(frac*360,1,1);
            rainbowLoop.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(frac*durationS),
                            new KeyValue(a.fillProperty(), col),
                            new KeyValue(a.strokeProperty(), col))
            );
        }
        rainbowLoop.setCycleCount(Animation.INDEFINITE);
        rainbowLoop.play();
    }
    private void stopHighlight(){
        if(rainbowLoop!=null) rainbowLoop.stop();
        arcs.forEach(x->{ x.setEffect(null); x.setStroke(SECTOR_BORDER); x.setStrokeWidth(SECTOR_BORDER_W); });
    }

    private void startSpinSound() {
        if (spinSoundFailed) {
            return;
        }
        if (spinPlayer == null) {
            var resource = Roue.class.getResource("/song-loto.mp3");
            if (resource == null) {
                System.err.println("Audio introuvable : /song-loto.mp3");
                spinSoundFailed = true;
                return;
            }
            try {
                spinPlayer = new MediaPlayer(new Media(resource.toExternalForm()));
                spinPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                spinPlayer.setOnError(() -> {
                    System.err.println("Erreur audio (roue) : " + spinPlayer.getError());
                    spinSoundFailed = true;
                });
            } catch (MediaException ex) {
                System.err.println("Impossible de charger l'audio de la roue : " + ex.getMessage());
                spinSoundFailed = true;
                return;
            }
        }
        spinPlayer.stop();
        spinPlayer.seek(Duration.ZERO);
        spinPlayer.play();
    }

    private void stopSpinSound() {
        if (spinPlayer != null) {
            spinPlayer.stop();
        }
    }

    private void addDecorRings(){
        Group rings = new Group();
        rings.setMouseTransparent(true);

        Circle baseShadow = new Circle(
                Main.WHEEL_RADIUS + 28,
                new RadialGradient(
                        0, 0, 0.5, 0.5, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(0, 0, 0, 0.35)),
                        new Stop(1, Color.color(0, 0, 0, 0.0))
                )
        );
        baseShadow.setTranslateY(18);

        Circle outerRim = new Circle(Main.WHEEL_RADIUS + 6, Color.TRANSPARENT);
        outerRim.setStroke(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#182848")),
                new Stop(1, Color.web("#0d1321"))
        ));
        outerRim.setStrokeWidth(12);

        Circle accentRing = new Circle(Main.WHEEL_RADIUS + 2, Color.TRANSPARENT);
        accentRing.setStroke(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Theme.ACCENT_LIGHT),
                new Stop(0.6, Theme.ACCENT),
                new Stop(1, Theme.ACCENT.darker())
        ));
        accentRing.setStrokeWidth(4);

        Circle innerGlow = new Circle(Main.WHEEL_RADIUS - 18,
                new RadialGradient(0, 0, 0.3, 0.3, 1.0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(1, 1, 1, 0.18)),
                        new Stop(1, Color.color(1, 1, 1, 0.0))));
        innerGlow.setOpacity(0.65);

        Group rivets = buildRivetRing(18, Main.WHEEL_RADIUS + 4);

        rings.getChildren().addAll(baseShadow, outerRim, accentRing, rivets, innerGlow);
        wheelGroup.getChildren().add(rings);
    }
    private Arc buildSector(double start,double extent,Color base,boolean loser){
        Arc arc = new Arc(0,0, Main.WHEEL_RADIUS, Main.WHEEL_RADIUS, start, extent);
        arc.setType(ArcType.ROUND);

        Paint p = loser
                ? new LinearGradient(
                        0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(70, 70, 70)),
                        new Stop(1, Color.rgb(25, 25, 25))
                )
                : new LinearGradient(0,0,1,1,true,CycleMethod.NO_CYCLE,
                new Stop(0, base.brighter()),
                new Stop(.45, base),
                new Stop(1, base.darker()));

        arc.setFill(p);
        arc.setStroke(SECTOR_BORDER);
        arc.setStrokeWidth(SECTOR_BORDER_W);
        return arc;
    }
    private Node buildHub(){
        Circle base = new Circle(HUB_RADIUS,
                new RadialGradient(0,0,.25,.25,1,true,CycleMethod.NO_CYCLE,
                        new Stop(0, Theme.ACCENT_LIGHT),
                        new Stop(1, Theme.ACCENT.darker())));
        base.setStroke(HUB_STROKE);
        base.setStrokeWidth(HUB_STROKE_W);
        base.setEffect(new DropShadow(18, Color.color(0, 0, 0, 0.55)));

        Circle top = new Circle(HUB_RADIUS * 0.55,
                new RadialGradient(0, 0, 0.2, 0.2, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(1, 1, 1, 0.85)),
                        new Stop(1, Color.color(1, 1, 1, 0.0))));
        top.setMouseTransparent(true);

        Circle ring = new Circle(HUB_RADIUS * 0.82, Color.TRANSPARENT);
        ring.setStroke(Color.color(1, 1, 1, 0.4));
        ring.setStrokeWidth(1.8);

        Group hub = new Group(base, ring, top);
        hub.setMouseTransparent(true);
        return hub;
    }

    private Node buildGlossOverlay() {
        Circle gloss = new Circle(Main.WHEEL_RADIUS - 10,
                new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(1, 1, 1, 0.35)),
                        new Stop(0.55, Color.color(1, 1, 1, 0.05)),
                        new Stop(1, Color.color(1, 1, 1, 0.0))));
        gloss.setMouseTransparent(true);
        return gloss;
    }

    private void buildSeatArrays(ObservableList<String> tickets,int losers){
        int P = tickets.size(), T = P + losers;
        seatNames  = new String[T];
        seatColors = new Color[T];

        int colorIdx=0;
        double step = (double)T / P, acc = 0;
        for(int i=0;i<P;i++){
            int idx = Math.min((int)Math.round(acc), T-1);
            while(seatNames[idx]!=null) idx = (idx+1)%T;
            seatNames[idx]  = tickets.get(i);
            seatColors[idx] = colorByIndex(colorIdx++);
            acc += step;
        }
        for(int i=0;i<T;i++){
            if(seatNames[i]==null) seatColors[i]= Color.rgb(30,30,30);
        }
    }

    private void enableDrag(){
        root.setOnMousePressed(e->{ dragX=e.getSceneX()-root.getTranslateX(); dragY=e.getSceneY()-root.getTranslateY(); root.setCursor(Cursor.CLOSED_HAND);} );
        root.setOnMouseDragged(e->{ root.setTranslateX(e.getSceneX()-dragX); root.setTranslateY(e.getSceneY()-dragY);} );
        root.setOnMouseReleased(e-> root.setCursor(Cursor.OPEN_HAND));
        root.setCursor(Cursor.OPEN_HAND);
    }

    private Group buildRivetRing(int count, double radius) {
        Group rivets = new Group();
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians((360.0 / count) * i);
            double x = Math.cos(angle) * radius;
            double y = Math.sin(angle) * radius;
            Circle rivet = new Circle(x, y, 3.5,
                    new RadialGradient(0, 0, 0.3, 0.3, 1, true, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.color(1, 1, 1, 0.9)),
                            new Stop(1, Color.color(0.7, 0.7, 0.7, 0.4))));
            rivet.setStroke(Color.color(0, 0, 0, 0.6));
            rivet.setStrokeWidth(0.8);
            rivet.setMouseTransparent(true);
            rivets.getChildren().add(rivet);
        }
        rivets.setMouseTransparent(true);
        return rivets;
    }
}
