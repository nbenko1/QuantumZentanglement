package com.aqwis.models;

import org.w3c.dom.*;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import static javafx.application.Platform.exit;

public class SimpleTiledWFCModel extends WFCModel {
    private boolean[][][] propagator;

    private List<Color[]> tiles;
    private int tilesize;
    private boolean black;

    private static Double attributeFromString(Node item, Double defaultValue) { return item == null ? defaultValue : Double.parseDouble(item.getNodeValue()); }
    private static Boolean attributeFromString(Node item, Boolean defaultValue) { return item == null ? defaultValue : Boolean.parseBoolean(item.getNodeValue()); }
    private static Integer attributeFromString(Node item, Integer defaultValue) { return item == null ? defaultValue : Integer.parseInt(item.getNodeValue()); }
    private static Character attributeFromString(Node item, Character defaultValue) { return item == null ? defaultValue : item.getNodeValue().toCharArray()[0]; }

    public SimpleTiledWFCModel(String name, String subsetName, int width, int height, boolean periodic, boolean black) throws Exception
    {
        FMX = width;
        FMY = height;
        this.periodic = periodic;
        this.black = black;

        File xmlFile = new File(String.format("samples/%s/data.xml", name));
        Document document = null;
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            document = docBuilder.parse(xmlFile);
        } catch (Exception e) {
            e.printStackTrace();
            exit();
        }

        assert document != null;

        Node firstChild = document.getFirstChild();
        NamedNodeMap firstChildNodeMap = firstChild.getAttributes();

        tilesize = attributeFromString(firstChildNodeMap.getNamedItem("size"), 16);
        boolean unique = attributeFromString(firstChildNodeMap.getNamedItem("unique"), false);

        List<String> subset = null;

        if (subsetName != null) {
            subset = new ArrayList<>();
            //NodeList secondaryNodeList = firstChild.getNextSibling().getNextSibling().getChildNodes();
            NodeList subsetList = document.getElementsByTagName("subset");
            for (int i = 0; i < subsetList.getLength(); i++) {
                Node subsetNode = subsetList.item(i);
                String subsetNodeName = subsetNode.getNodeName();
                NamedNodeMap subsetNodeMap = subsetNode.getAttributes();
                if (!subsetNodeName.equals("#comment") && subsetNodeMap.getNamedItem("name").getNodeValue().equals(subsetName)) {
                    //NodeList tertiaryNodeList = subsetNode.getChildNodes();
                    NodeList subTileList = ((Element) subsetNode).getElementsByTagName("tile");
                    for (int j = 0; j < subTileList.getLength(); j++) {
                        Node stile = subTileList.item(j);
                        NamedNodeMap stileNodeMap = stile.getAttributes();
                        subset.add(stileNodeMap.getNamedItem("name").getNodeValue());
                    }
                }
            }
        }

        Function<BiFunction<Integer, Integer, Color>, Color[]> tile = f ->
        {
            Color[] result = new Color[tilesize * tilesize];
            for (int y = 0; y < tilesize; y++) {
                for (int x = 0; x < tilesize; x++) {
                    result[x + y * tilesize] = f.apply(x, y);
                }
            }
            return result;
        };

        Function<Color[], Color[]> rotate = array -> tile.apply((x, y) -> array[tilesize - 1 - y + x * tilesize]);

        tiles = new ArrayList<>();
        List<Double> tempStationary = new ArrayList<>();

        List<Integer[]> action = new ArrayList<>();
        Map<String, Integer> firstOccurrence = new HashMap<>();

        //NodeList childNodes = firstChild.getChildNodes();
        Node tilesNode = document.getElementsByTagName("tiles").item(0);
        NodeList tileNodes = ((Element) tilesNode).getElementsByTagName("tile");
        for (int i = 0; i < tileNodes.getLength(); i++)
        {
            Node xtile = tileNodes.item(i);
            NamedNodeMap xtileMap = xtile.getAttributes();
            String tilename = xtileMap.getNamedItem("name").getNodeValue();

            if (subset != null && !subset.contains(tilename)) {
                continue;
            }

            Function<Integer, Integer> a, b;
            int cardinality;

            char sym = attributeFromString(xtileMap.getNamedItem("symmetry"), 'X');
            if (sym == 'L')
            {
                cardinality = 4;
                a = v -> (v + 1) % 4;
                b = v -> v % 2 == 0 ? v + 1 : v - 1;
            }
            else if (sym == 'T')
            {
                cardinality = 4;
                a = v -> (v + 1) % 4;
                b = v -> v % 2 == 0 ? v : 4 - v;
            }
            else if (sym == 'I')
            {
                cardinality = 2;
                a = v -> 1 - v;
                b = v -> v;
            }
            else if (sym == '\\')
            {
                cardinality = 2;
                a = v -> 1 - v;
                b = v -> 1 - v;
            }
            else
            {
                cardinality = 1;
                a = v -> v;
                b = v -> v;
            }

            T = action.size();
            firstOccurrence.put(tilename, T);

            Integer[][] map = new Integer[cardinality][];
            for (int t = 0; t < cardinality; t++)
            {
                map[t] = new Integer[8];

                map[t][0] = t;
                map[t][1] = a.apply(t);
                map[t][2] = a.apply(a.apply(t));
                map[t][3] = a.apply(a.apply(a.apply(t)));
                map[t][4] = b.apply(t);
                map[t][5] = b.apply(a.apply(t));
                map[t][6] = b.apply(a.apply(a.apply(t)));
                map[t][7] = b.apply(a.apply(a.apply(a.apply(t))));

                for (int s = 0; s < 8; s++) {
                    map[t][s] += T;
                }

                action.add(map[t]);
            }

            if (unique)
            {
                for (int t = 0; t < cardinality; t++)
                {
                    File bitmapFile = new File(String.format("samples/%s/%s %d.bmp", name, tilename, t));
                    BufferedImage bitmap = ImageIO.read(bitmapFile);
                    tiles.add(tile.apply((x, y) -> new Color(bitmap.getRGB(x, y))));
                }
            }
            else
            {
                File bitmapFile = new File(String.format("samples/%s/%s.bmp", name, tilename));
                BufferedImage bitmap = ImageIO.read(bitmapFile);
                tiles.add(tile.apply((x, y) -> new Color(bitmap.getRGB(x, y))));
                for (int t = 1; t < cardinality; t++) {
                    tiles.add(rotate.apply(tiles.get(T + t - 1)));
                }
            }

            for (int t = 0; t < cardinality; t++) {
                tempStationary.add(attributeFromString(xtileMap.getNamedItem("weight"), new Double(1.0f)));
            }
        }

        T = action.size();
        stationary = new double[tempStationary.size()];

        int i = 0;
        for (Double el : tempStationary) {
            stationary[i] = el;
            i++;
        }

        propagator = new boolean[4][][];
        for (int d = 0; d < 4; d++)
        {
            propagator[d] = new boolean[T][];
            for (int t = 0; t < T; t++) {
                propagator[d][t] = new boolean[T];
            }
        }

        wave = new boolean[FMX][][];
        changes = new boolean[FMX][];
        for (int x = 0; x < FMX; x++)
        {
            wave[x] = new boolean[FMY][];
            changes[x] = new boolean[FMY];
            for (int y = 0; y < FMY; y++) wave[x][y] = new boolean[T];
        }

        //NodeList further = firstChild.getNextSibling().getChildNodes();
        NodeList neighborList = document.getElementsByTagName("neighbor");
        for (int k = 0; k < neighborList.getLength(); k++)
        {
            Node xneighbor = neighborList.item(k);
            NamedNodeMap xneighborAttributes = xneighbor.getAttributes();
            String leftString = xneighborAttributes.getNamedItem("left").getNodeValue();
            String rightString = xneighborAttributes.getNamedItem("right").getNodeValue();

            String[] left = leftString.split(" ");
            String[] right = rightString.split(" ");

            if (subset != null && (!subset.contains(left[0]) || !subset.contains(right[0]))) {
                continue;
            }

            int L = action.get(firstOccurrence.get(left[0]))[left.length == 1 ? 0 : Integer.parseInt(left[1])], D = action.get(L)[1];
            int R = action.get(firstOccurrence.get(right[0]))[right.length == 1 ? 0 : Integer.parseInt(right[1])], U = action.get(R)[1];

            propagator[0][R][L] = true;
            propagator[0][action.get(R)[6]][action.get(L)[6]] = true;
            propagator[0][action.get(L)[4]][action.get(R)[4]] = true;
            propagator[0][action.get(L)[2]][action.get(R)[2]] = true;

            propagator[1][U][D] = true;
            propagator[1][action.get(D)[6]][action.get(U)[6]] = true;
            propagator[1][action.get(U)[4]][action.get(D)[4]] = true;
            propagator[1][action.get(D)[2]][action.get(U)[2]] = true;
        }

        for (int t2 = 0; t2 < T; t2++)
            for (int t1 = 0; t1 < T; t1++)
            {
                propagator[2][t2][t1] = propagator[0][t1][t2];
                propagator[3][t2][t1] = propagator[1][t1][t2];
            }
    }

    protected Boolean propagate()
    {
        boolean change = false, b;
        for (int x2 = 0; x2 < FMX; x2++) {
            for (int y2 = 0; y2 < FMY; y2++) {
                for (int d = 0; d < 4; d++) {
                    int x1 = x2, y1 = y2;
                    if (d == 0) {
                        if (x2 == 0) {
                            if (!periodic) continue;
                            else x1 = FMX - 1;
                        } else x1 = x2 - 1;
                    } else if (d == 1) {
                        if (y2 == FMY - 1) {
                            if (!periodic) continue;
                            else y1 = 0;
                        } else y1 = y2 + 1;
                    } else if (d == 2) {
                        if (x2 == FMX - 1) {
                            if (!periodic) continue;
                            else x1 = 0;
                        } else x1 = x2 + 1;
                    } else {
                        if (y2 == 0) {
                            if (!periodic) continue;
                            else y1 = FMY - 1;
                        } else y1 = y2 - 1;
                    }

                    if (!changes[x1][y1]) continue;
                    boolean[] w = wave[x1][y1];
                    boolean[] wc = wave[x2][y2];

                    for (int t2 = 0; t2 < T; t2++)
                        if (wc[t2]) {
                            boolean[] p = propagator[d][t2];

                            b = false;
                            for (int t1 = 0; t1 < T && !b; t1++) if (w[t1]) b = p[t1];
                            if (!b) {
                                wave[x2][y2][t2] = false;
                                changes[x2][y2] = true;
                                change = true;
                            }
                        }
                }
            }
        }

        return change;
    }

    protected boolean onBoundary(int x, int y) {
        return false;
    }

    public BufferedImage graphics()
    {
        BufferedImage result = new BufferedImage(FMX * tilesize, FMY * tilesize, BufferedImage.TYPE_INT_RGB);

        //int[] bmpData = new int[result.getHeight() * result.getWidth()];

        for (int x = 0; x < FMX; x++) for (int y = 0; y < FMY; y++)
        {
            boolean[] a = wave[x][y];
            //int amount = (from b in a where b select 1).Sum();
            int amount = 0;
            for (boolean el : wave[x][y]) {
                if (el) {
                    amount += 1;
                }
            }

            // double lambda = 1.0 / (from t in Enumerable.Range(0, T) where a[t] select stationary[t]).Sum();
            double divisor = 0;
            for (int el : IntStream.range(0, T).toArray()) {
                if (a[el]) {
                    divisor += stationary[el];
                }
            }
            double lambda = 1.0/divisor;

            for (int yt = 0; yt < tilesize; yt++) for (int xt = 0; xt < tilesize; xt++)
            {
                if (black && amount == T) {
                    //bmpData[x * tilesize + xt + (y * tilesize + yt) * FMX * tilesize] = ;
                    result.setRGB(x*tilesize+xt, y*tilesize+yt, 0xff000000);
                } else {
                    double r = 0, g = 0, b = 0;
                    for (int t = 0; t < T; t++) if (wave[x][y][t]) {
                        Color c = tiles.get(t)[xt + yt * tilesize];
                        r += (double) c.getRed() * stationary[t] * lambda;
                        g += (double) c.getGreen() * stationary[t] * lambda;
                        b += (double) c.getBlue() * stationary[t] * lambda;
                    }

                    //bmpData[x * tilesize + xt + (y * tilesize + yt) * FMX * tilesize] = 0xff000000 | ((int)r << 16) | ((int)g << 8) | (int)b;
                    result.setRGB(x*tilesize+xt, y*tilesize+yt, 0xff000000 | ((int)r << 16) | ((int)g << 8) | (int)b);
                }
            }
        }

        /*
        var bits = result.LockBits(new Rectangle(0, 0, result.Width, result.Height), System.Drawing.Imaging.ImageLockMode.WriteOnly, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
        System.Runtime.InteropServices.Marshal.Copy(bmpData, 0, bits.Scan0, bmpData.Length);
        result.UnlockBits(bits);
        */

        return result;
    }
}
