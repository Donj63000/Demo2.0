package org.example;

import javafx.animation.*;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.Glow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.*;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Roue de loterie – une couleur par ticket ; la case gagnante clignote
 * en arc‑en‑ciel grâce à un Timeline cyclique, avec pulsation + halo.
 */
public class Roue {

    /* ============================================================ */
    /* 1)  Paramètres visuels                                       */
    /* ============================================================ */
    private static final double HUB_RADIUS      = Main.WHEEL_RADIUS * .28;
    private static final Color  HUB_STROKE      = Color.web("#d4af37");
    private static final double HUB_STROKE_W    = 3;

    private static final Color  SECTOR_BORDER   = Color.rgb(0,0,0,.25);
    private static final double SECTOR_BORDER_W = 1.1;

    private static final double GOLDEN_ANGLE    = 137.50776405003785;

    /* ============================================================ */
    /* 2)  Couleur unique par index                                 */
    /* ============================================================ */
    private static Color colorByIndex(int idx){
        double h = (idx * GOLDEN_ANGLE) % 360;
        return Color.hsb(h, .85, .90);
    }

    /* ============================================================ */
    /* 3)  Attributs                                                */
    /* ============================================================ */
    private final StackPane root;
    private final Group     wheelGroup;
    private final RotateTransition rot;
    private final Resultat  resultat;
    private final List<Arc> arcs = new ArrayList<>();

    private String[] seatNames;
    private Color[]  seatColors;

    private Polygon  cursor;
    private ParallelTransition winFx;
    private Consumer<String>   spinCallback;

    // drag
    private double dragX, dragY;

    /* ============================================================ */
    /* 4)  Constructeur                                             */
    /* ============================================================ */
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

        cursor = new Polygon( 0,-(Main.WHEEL_RADIUS+10), -14,-(Main.WHEEL_RADIUS-6), 14,-(Main.WHEEL_RADIUS-6));
        cursor.setFill(Color.WHITE);
        cursor.setStroke(Color.web("#ec407a"));
        cursor.setStrokeWidth(1.3);
        root.getChildren().add(cursor);

        enableDrag();
    }

    /* ============================================================ */
    /* 5)  API                                                      */
    /* ============================================================ */
    public Node getRootPane(){ return root; }
    public void resetPosition(){ root.setTranslateX(0); root.setTranslateY(0); }
    public void setOnSpinFinished(Consumer<String> cb){ spinCallback = cb; }

    /* ============================================================ */
    /* 6)  Construction                                             */
    /* ============================================================ */
    public void updateWheelDisplay(ObservableList<String> tickets){
        buildSeatArrays(tickets, OptionRoue.getLosingTickets());

        wheelGroup.setRotate(0);
        rot.stop();
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
        wheelGroup.getChildren().add(buildHub());
    }

    /* ============================================================ */
    /* 7)  Spin (2 signatures)                                      */
    /* ============================================================ */
    public void spinTheWheel(ObservableList<String> t){ updateWheelDisplay(t); spinTheWheel(); }
    public void spinTheWheel(){
        if(winFx!=null){ winFx.stop(); clearHighlight(); }

        int total = seatNames==null?0:seatNames.length;
        if(total==0){ resultat.setMessage("Aucun ticket – impossible de lancer la roue."); return; }

        int idx = ThreadLocalRandom.current().nextInt(total);
        double step = 360d/total;
        double end  = 1080 + idx*step + step/2 - 90;

        rot.setDuration(Duration.seconds(OptionRoue.getSpinDuration()));
        rot.setNode(wheelGroup);
        rot.setFromAngle(wheelGroup.getRotate());
        rot.setToAngle(wheelGroup.getRotate() + end);
        rot.setInterpolator(Interpolator.EASE_OUT);
        rot.setOnFinished(e -> {
            String pseudo = seatNames[idx];
            resultat.setMessage(pseudo!=null ? pseudo+" a gagné !" : "Perdu !");
            if(spinCallback!=null) spinCallback.accept(pseudo);
            highlightWinner(idx);
        });
        rot.play();
    }

    /* ============================================================ */
    /* 8)  Effet gagnant : arc‑en‑ciel dynamique                    */
    /* ============================================================ */
    private void highlightWinner(int idx){
        if(idx<0||idx>=arcs.size()) return;
        Arc a = arcs.get(idx);

        /* --- Halo + épaisseur ------------------------------------ */
        a.setStrokeWidth(SECTOR_BORDER_W*2);
        Glow g = new Glow(.8); a.setEffect(g);

        /* --- Pulsation ------------------------------------------- */
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(.55), a);
        pulse.setFromX(1); pulse.setFromY(1);
        pulse.setToX(1.14); pulse.setToY(1.14);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);

        /* --- Cycle arc‑en‑ciel (fill ET stroke) ------------------ */
        Timeline rainbow = new Timeline();
        double durationS = 1.2;              // durée d’un cycle complet
        int    steps     = 12;               // résolution couleur
        for(int i=0;i<=steps;i++){
            double frac = (double)i/steps;
            Color col = Color.hsb(frac*360,1,1);
            rainbow.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(frac*durationS),
                            new KeyValue(a.fillProperty(), col),
                            new KeyValue(a.strokeProperty(), col))
            );
        }
        rainbow.setCycleCount(Animation.INDEFINITE);

        winFx = new ParallelTransition(pulse, rainbow);
        winFx.setOnFinished(e-> clearHighlight());
        winFx.play();
    }
    private void clearHighlight(){
        arcs.forEach(x->{ x.setEffect(null); x.setStroke(SECTOR_BORDER); x.setStrokeWidth(SECTOR_BORDER_W); });
    }

    /* ============================================================ */
    /* 9)  Dessin : secteurs, anneaux, moyeu                        */
    /* ============================================================ */
    private void addDecorRings(){
        Circle border = new Circle(Main.WHEEL_RADIUS+.6, Color.TRANSPARENT);
        border.setStroke(Color.BLACK); border.setStrokeWidth(1.2);

        Circle gold = new Circle(Main.WHEEL_RADIUS+4, Color.TRANSPARENT);
        gold.setStroke(Color.web("#ffd54f")); gold.setStrokeWidth(6);

        wheelGroup.getChildren().addAll(border, gold);
    }
    private Arc buildSector(double start,double extent,Color base,boolean loser){
        Arc arc = new Arc(0,0, Main.WHEEL_RADIUS, Main.WHEEL_RADIUS, start, extent);
        arc.setType(ArcType.ROUND);

        Paint p = loser
                ? Color.rgb(35,35,35)
                : new LinearGradient(0,0,1,1,true,CycleMethod.NO_CYCLE,
                new Stop(0, base.brighter()),
                new Stop(.45, base),
                new Stop(1, base.darker()));

        arc.setFill(p);
        arc.setStroke(SECTOR_BORDER);
        arc.setStrokeWidth(SECTOR_BORDER_W);
        return arc;
    }
    private Circle buildHub(){
        Circle c = new Circle(HUB_RADIUS,
                new RadialGradient(0,0,.3,.3,1,true,CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#fffef9")), new Stop(1, Color.web("#e0c97f"))));
        c.setStroke(HUB_STROKE); c.setStrokeWidth(HUB_STROKE_W);
        return c;
    }

    /* ============================================================ */
    /* 10)  Données : distribution des tickets                      */
    /* ============================================================ */
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
        // perdants gris
        for(int i=0;i<T;i++){
            if(seatNames[i]==null) seatColors[i]= Color.rgb(30,30,30);
        }
    }

    /* ============================================================ */
    /* 11)  Drag & drop                                             */
    /* ============================================================ */
    private void enableDrag(){
        root.setOnMousePressed(e->{ dragX=e.getSceneX()-root.getTranslateX(); dragY=e.getSceneY()-root.getTranslateY(); root.setCursor(Cursor.CLOSED_HAND);} );
        root.setOnMouseDragged(e->{ root.setTranslateX(e.getSceneX()-dragX); root.setTranslateY(e.getSceneY()-dragY);} );
        root.setOnMouseReleased(e-> root.setCursor(Cursor.OPEN_HAND));
        root.setCursor(Cursor.OPEN_HAND);
    }
}
