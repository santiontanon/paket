/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package paket.compiler.optimizers;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import paket.compiler.PAKETConfig;
import paket.compiler.PAKETTokenizer;
import paket.compiler.Token;
import paket.util.Pair;

/**
 *
 * @author santi
 */
public abstract class PAKETOptimizer {
    Random r = new Random();
    int randomSeed = 0;
    boolean firstObjectMustRemainFirst = false;
    
    String name = "";
    public List<String> state = new ArrayList<>();
    boolean anyImprovement = true;
    
    
    public PAKETOptimizer(String a_name, int a_randomSeed,
                          boolean a_firstObjectMustRemainFirst)
    {
        name = a_name;
        randomSeed = a_randomSeed;
        firstObjectMustRemainFirst = a_firstObjectMustRemainFirst;
    }

    
    public void loadOptimizerState(String destinationFolder, PAKETConfig config) throws Exception
    {
        state.clear();
        anyImprovement = true;
        String fileName = destinationFolder + "/PAKETOptimizerState-"+name+".txt";
        File f = new File(fileName);
        if (!f.exists()) return;
        PAKETTokenizer tokenizer = new PAKETTokenizer(fileName);
        Token t = tokenizer.nextToken();
        if (t.type != Token.TOKEN_TYPE_NUMBER) {
            config.info("No optimization state found, starting from scratch...");
        }
        int nSentences = Integer.parseInt(t.value);
        for(int i = 0;i<nSentences;i++) {
            t = tokenizer.nextToken();
            if (t.type != Token.TOKEN_TYPE_STRING) {
                config.error("Optimization state loading failed!");
                state.clear();
                return;
            }
            state.add(t.value);
        }
        t = tokenizer.nextToken();
        anyImprovement = t.value.equals("true");
    }
    
    
    public List<Object> objectsFromState(List<? extends Object> objects) throws Exception
    {
        if (state.size() != objects.size()) return null;
        List<Object> l = new ArrayList<>();
        for(int i = 0;i<objects.size();i++) {
            l.add(null);
        }
        for(Object o:objects) {
            String ID = IDFromObject(o);
            int index = state.indexOf(ID);
            if (index == -1) return null;
            l.set(index, o);
        }
        return l;
    }
    
    
    public void saveOptimizerState(String destinationFolder) throws Exception
    {
        String fileName = destinationFolder + "/PAKETOptimizerState-"+name+".txt";
        FileWriter fw = new FileWriter(fileName);
        fw.write(""+state.size()+"\n");
        for(String line:state) {
            fw.write("\"" + line.replace("\"", "\\\"") + "\"\n");
        }
        fw.write(anyImprovement ? "true":"false");
        fw.flush();
        fw.close();
    }
    
    
    public List<? extends Object> optimizeCompressionOrder(
            List<? extends Object> objects,
            PAKETConfig config) throws Exception
    {
        r.setSeed(randomSeed);
        
        HashMap<String,Integer> IDIndexMap = new HashMap<>();
        List<String> objectOrder = new ArrayList<>();
        for(int i = 0;i<objects.size();i++) {
            String ID = IDFromObject(objects.get(i));
            objectOrder.add(ID);
            IDIndexMap.put(ID, i);
        }        
        
        // reset order based on optimization state:
        HashMap<String, Integer> resetOrder = new HashMap<>();
        for(Object o:objects) {
            String ID = IDFromObject(o);
            if (state.contains(ID)) {
                resetOrder.put(ID, state.indexOf(ID));
            } else {
                if (firstObjectMustRemainFirst && o == objects.get(0)) {
                    resetOrder.put(ID, -1);
                } else {
                    resetOrder.put(ID, r.nextInt(objects.size()));
                }
            }
        }
        Collections.sort(objectOrder, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return resetOrder.get(o1).compareTo(resetOrder.get(o2));
            }
        });
        // Perform optimization only if the object IDs are different from 
        // before, or if there was new optimizations in the last iteration:
        if (anyImprovement ||
            !objectOrder.equals(state)) {
            List<Integer> objectTypeOrderIndexes = new ArrayList<>();
            for(String ID:objectOrder) {
                objectTypeOrderIndexes.add(IDIndexMap.get(ID));
            }
            objectTypeOrderIndexes = optimizeCompressionOrderInternal(objectTypeOrderIndexes, objects, config);
            objectOrder.clear();
            for(int i:objectTypeOrderIndexes) {
                String ID = IDFromObject(objects.get(i));
                objectOrder.add(ID);
            }
            if (objectOrder.equals(state)) {
                anyImprovement = false;
            } else {
                state.clear();
                state.addAll(objectOrder);
                anyImprovement = true;
            }
        }

        // update all the object IDs in the rooms to agree with the new order:
        List<Object> objects2 = new ArrayList<>();
        for(String ID:objectOrder) {
            objects2.add(objects.get(IDIndexMap.get(ID)));
        }
        
        return objects2;
    }
           
    
    List<Integer> optimizeCompressionOrderInternal(
            List<Integer> original_order, 
            List<? extends Object> objects,
            PAKETConfig config) throws Exception
    {

        String compressorDuringOptimization = "plt";
        String targetCompressor = PAKETConfig.compressorExtension[config.compressor];
        List<Integer> order = new ArrayList<>();
        order.addAll(original_order);

        int initial_order_size = extimateSizeWithOrder(original_order, objects, targetCompressor, config);
        int initial_size = extimateSizeWithOrder(order, objects, compressorDuringOptimization, config);
        int best = initial_size;
        config.info("optimizeCompressionOrder: " + name + " with " + objects.size() + " objects (uncompressed size: " + uncompressedSize(objects, config) + ").");
        config.info("optimizeCompressionOrder: Original order size (with target compressor): " + initial_order_size);
        config.info("optimizeCompressionOrder: Initial size: " + initial_size);
        double threshold = 1.0; // 1.0 means doing it sistematically, lower values run faster, but might not result in the best results
        double temperature = 0.0;
        double temperature_decay = 0.8;
        boolean repeat = true;
        int repeats_left = config.maxObjectOptimizationIterations;

        while(repeat){
            repeat = false;
//            config.info("temperature: " + temperature);
            int startIndex = 0;
            if (firstObjectMustRemainFirst) {
                startIndex = 1;
            }
            for(int idx1 = startIndex;idx1<order.size();idx1++) {
                for(int idx2 =idx1+1;idx2<order.size();idx2++) {
                    if (r.nextDouble() > threshold) continue;
                    Integer tmp1 = order.get(idx1);
                    Integer tmp2 = order.get(idx2);
                    order.set(idx1, tmp2);
                    order.set(idx2, tmp1);
                    int size = extimateSizeWithOrder(order, objects, compressorDuringOptimization, config);
                    if (size < best) {
                        config.debug(size + " ");
                        best = size;
                        repeat = true;
                    } else if (size == best && r.nextDouble() > 0.5) {
                        config.debug(size + "* ");
                        best = size;
                        // repeat = true;
                    } else {
                        if (r.nextDouble() > temperature) {
                            // undo the swap:
                            order.set(idx1, tmp1);
                            order.set(idx2, tmp2);
                        } else {
                            best = size;
                            repeat = true;                            
                        }
                    }
                }
            }
            temperature *= temperature_decay;
            repeats_left--;
            if (repeats_left <= 0) break;
        }
        
        int final_order_size = extimateSizeWithOrder(order, objects, targetCompressor, config);
        config.info("optimizeCompressionOrder: after optimization (with target compressor): " + final_order_size);
        if (final_order_size > initial_order_size) {
            order = original_order;
            config.info("optimizeCompressionOrder: worse than initial optimization, ignoring it!");
        } else if (final_order_size == initial_order_size) {
            // to prevent infinite optimization loops
            order = original_order;
        }
        return order;
    }


    public abstract String IDFromObject(Object object) throws Exception;
    public abstract int uncompressedSize(List<? extends Object> objects, PAKETConfig config) throws Exception;    
    public abstract int extimateSizeWithOrder(List<Integer> order, List<? extends Object> objects, String compressor, PAKETConfig config) throws Exception;    
    public abstract List<Integer> uncompressedBytes(Object object, PAKETConfig config) throws Exception;
    
    

    public int[][] dp = null;
    public double editDistance(List<Integer> x, List<Integer> y) {
        if (dp == null || x.size() + 1 > dp.length || y.size() + 1 > dp[0].length) {
            dp = new int[x.size() + 1][y.size() + 1];
        }

        for (int i = 0; i <= x.size(); i++) {
            for (int j = 0; j <= y.size(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                }
                else if (j == 0) {
                    dp[i][j] = i;
                }
                else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j - 1] + (x.get(i - 1).equals(y.get(j - 1)) ? 0 : 1), 
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1));
                }
            }
        }
        double d = dp[x.size()][y.size()] / (double)Math.max(x.size(), y.size());
//        System.out.println("EditDistance: " + x + " -> " + y);
//        System.out.println("EditDistance: " + d);
        return d;
    }            
    
    
    public double getHeuristicScore(List<Integer> x, List<List<Integer>> sorted)
    {
        int n_previous_to_consider = 1;
        double score = 0.0;
        double weight = 1.0;
        for(int i = 0;i < n_previous_to_consider; i++) {
            int idx = sorted.size() - 1 - i;
            if (idx >= 0) {
                double d = editDistance(x, sorted.get(idx));
                score += d * weight;
            }
            weight *= 0.9;
        }
        return score;
    }

    
    public List<? extends Object> heuristicCompressionOrder(
            List<? extends Object> objects,
            PAKETConfig config) throws Exception
    {
        // See if we can reuse the state or we need to reoptimize:
        List<Object> stateSorted = objectsFromState(objects);
        List<Integer> stateOrder = null;
        if (stateSorted != null) {
            stateOrder = new ArrayList<>();
            for(Object o:stateSorted) {
                stateOrder.add(objects.indexOf(o));
            }
        }
        
        // Sort objects by similarity, trying to put those that are most similar
        // together:
        List<Pair<List<Integer>, Integer>> left = new ArrayList<>();
        List<List<Integer>> sorted_bytes = new ArrayList<>();
        List<Object> heuristicSorted = new ArrayList<>();
        List<Integer> originalOrder = new ArrayList<>();
        List<Integer> newOrder = new ArrayList<>();
        
        if (!objects.isEmpty()) {
            originalOrder.add(0);
        }
        for(int i = 1;i<objects.size();i++) {
            left.add(new Pair<>(uncompressedBytes(objects.get(i), config), i));
            originalOrder.add(i);
        }

        int initialSize = extimateSizeWithOrder(originalOrder, objects, PAKETConfig.compressorExtension[config.compressor], config);
        config.info("heuristicCompressionOrder ("+name+"): initial size " + initialSize);
        
        // Start with the very first:
        if (!objects.isEmpty()) {
            sorted_bytes.add(uncompressedBytes(objects.get(0), config));
            heuristicSorted.add(objects.get(0));
            newOrder.add(0);
        }
        
        // Add the rest:
        while(!left.isEmpty()) {
            Pair<List<Integer>, Integer> best = null;
            double best_score = 0.0;
//            System.out.println("----");
            for(Pair<List<Integer>, Integer> o:left) {
                double score = getHeuristicScore(o.m_a, sorted_bytes);
                if (best == null || score < best_score) {
                    best = o;
                    best_score = score;
//                    System.out.println("new best: " + best_score);
                }
            }
            left.remove(best);
            heuristicSorted.add(objects.get(best.m_b));
            sorted_bytes.add(best.m_a);
            newOrder.add(best.m_b);
        }
        
        int heuristicSize = extimateSizeWithOrder(newOrder, objects, PAKETConfig.compressorExtension[config.compressor], config);
        config.info("heuristicCompressionOrder ("+name+"): new size " + heuristicSize);
        if (initialSize < heuristicSize) {
            if (stateOrder != null) {
                int stateSize = extimateSizeWithOrder(stateOrder, objects, PAKETConfig.compressorExtension[config.compressor], config);
                config.info("heuristicCompressionOrder ("+name+"): state size " + stateSize);
                if (stateSize < initialSize) {
                    return stateSorted;
                } else {
                    // Clone "objects" to prevent the compiler messing up later:
                    List<Object> l = new ArrayList<>();
                    l.addAll(objects);
                    return l;
                }
            } else {
                // Clone "objects" to prevent the compiler messing up later:
                List<Object> l = new ArrayList<>();
                l.addAll(objects);
                return l;
            }       
        } else {
            if (stateOrder != null) {
                int stateSize = extimateSizeWithOrder(stateOrder, objects, PAKETConfig.compressorExtension[config.compressor], config);
                config.info("heuristicCompressionOrder ("+name+"): state size " + stateSize);
                if (stateSize < heuristicSize) {
                    return stateSorted;
                } else {
                    return heuristicSorted;
                }
            } else {
                return heuristicSorted;
            } 
        }
    }
}
