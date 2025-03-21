package grail;

import org.neo4j.driver.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class CypherBenchmarkTest {
    public final Driver driver = GraphDatabase.driver("bolt://127.0.0.1:7687", AuthTokens.basic("neo4j", "neo4j"));
    public final static String serializable = "match (n:txn) with collect(n) as nodes call apoc.nodes.cycles(nodes) yield path return path limit 1";
    public final static String si = "match (n:txn) with collect(n) as nodes call apoc.nodes.cycles(nodes) yield path return path";
    public final static Pattern pattern = Pattern.compile(":(\\w{2})");
    public final static String rw = "rw";
    public static final String pl2 = "match (n:txn) with collect(n) as nodes call apoc.nodes.cycles(nodes, {relTypes: ['ww','wr']}) yield path return path limit 1";
    public static final String pl1 = "match (n:txn) with collect(n) as nodes call apoc.nodes.cycles(nodes, {relTypes: ['ww']}) yield path return path limit 1";
    public static final String dropproj = "call gds.graph.drop('pbt')";
    public static final String scc = "call gds.alpha.scc.write('pbt', {}) yield maxSetSize as s return s";
    public static final String serproj = "CALL gds.graph.project.cypher( 'pbt', 'MATCH (n:txn) RETURN id(n) AS id', 'MATCH (n:txn)-->(n2:txn) RETURN id(n) AS source, id(n2) AS target')";
    public static final String pl2proj = "CALL gds.graph.project.cypher( 'pbt', 'MATCH (n:txn) RETURN id(n) AS id', 'MATCH (n:txn)-[:ww|wr]->(n2:txn) RETURN id(n) AS source, id(n2) AS target')";
    public static final String pl1proj = "CALL gds.graph.project.cypher( 'pbt', 'MATCH (n:txn) RETURN id(n) AS id', 'MATCH (n:txn)-[:ww]->(n2:txn) RETURN id(n) AS source, id(n2) AS target')";
    public static final String sccstream = "CALL gds.alpha.scc.stream('pbt', {}) YIELD nodeId, componentId WITH componentId, COLLECT(nodeId) AS nodes, COUNT(nodeId) AS num WHERE num > 1 RETURN nodes";
    public static final String smallcycle = "match (n:txn) where id(n) in %s with collect(n) as nodes call apoc.nodes.cycles(nodes) yield path return path";

    @Benchmark
    public void SerTest() throws Exception {
        try (Session session = driver.session()) {
            session.run(serializable);
        }
    }

    @Benchmark
    public void SITest() throws Exception{
        try(Session session = driver.session()) {
            Result result = session.run(si);
            while (result.hasNext()) {
                Record next = result.next();
                String res = next.get("path").toString();
                Matcher matcher = pattern.matcher(res);
                List<String> cycle = new ArrayList<>();
                while (matcher.find())
                    cycle.add(matcher.group(1));
                boolean findRW = false;
                for (int i = 1; i <= cycle.size(); i++) {
                    if (i == cycle.size()) {
                        if (cycle.get(i - 1).equals(rw) && cycle.get(0).equals(rw)) {
                            findRW = true;
                            break;
                        }
                    } else {
                        if (cycle.get(i - 1).equals(rw) && cycle.get(i).equals(rw)) {
                            findRW = true;
                            break;
                        }
                    }
                }
                if (!findRW) {
                    break;
                }
            }
        }
    }

    @Benchmark
    public void PSITest() throws Exception{
        // the kernel of neo4j is java
        try(Session session = driver.session()) {
            Result result = session.run(si);
            while (result.hasNext()) {
                Record next = result.next();
                String res = next.get("path").toString();
                Matcher matcher = pattern.matcher(res);
                List<String> cycle = new ArrayList<>();
                while (matcher.find())
                    cycle.add(matcher.group(1));
                if (cycle.stream().filter(rw::equals).count() < 2)
                    break;
            }
        }
    }

    @Benchmark
    public void PL2Test() throws Exception {
        try (Session session = driver.session()) {
            session.run(pl2);
        }
    }

    @Benchmark
    public void PL1Test() throws Exception {
        try (Session session = driver.session()){
            session.run(pl1);
        }
    }

    @Benchmark
    public void Q1SerProjTest() throws Exception {
        try (Session session = driver.session()){
            session.run(dropproj);
            session.run(serproj);
        }
    }

    @Benchmark
    public void Q2SerSCCTest() throws Exception {
        try (Session session = driver.session()){
            session.run(scc);
        }
    }

    @Benchmark
    public void Q3_SISCCTest() throws Exception {
        try (Session session = driver.session()){
            Result result = session.run(sccstream);
            while (result.hasNext()) {
                String list = result.next().get(0).toString();
                Result innerResult = session.run(String.format(smallcycle, list));
                while (innerResult.hasNext()) {
                    String res = innerResult.next().get("path").toString();
                    Matcher matcher = pattern.matcher(res);
                    List<String> cycle = new ArrayList<>();
                    while (matcher.find())
                        cycle.add(matcher.group(1));
                    boolean findRW = false;
                    for (int i = 1; i <= cycle.size(); i++) {
                        if (i == cycle.size()) {
                            if (cycle.get(i - 1).equals(rw) && cycle.get(0).equals(rw)) {
                                findRW = true;
                                break;
                            }
                        } else {
                            if (cycle.get(i - 1).equals(rw) && cycle.get(i).equals(rw)) {
                                findRW = true;
                                break;
                            }
                        }
                    }
                    if (!findRW) {
                        return;
                    }
                }
            }
        }
    }

    @Benchmark
    public void Q4PSISCCTest() throws Exception {
        try (Session session = driver.session()){
            Result result = session.run(sccstream);
            while (result.hasNext()) {
                String list = result.next().get(0).toString();
                Result innerResult = session.run(String.format(smallcycle, list));
                while (innerResult.hasNext()) {
                    String res = innerResult.next().get("path").toString();
                    Matcher matcher = pattern.matcher(res);
                    List<String> cycle = new ArrayList<>();
                    while (matcher.find())
                        cycle.add(matcher.group(1));
                    if (cycle.stream().filter(rw::equals).count() < 2)
                        return;
                }
            }
        }
    }

    @Benchmark
    public void Q5PL2ProjTest() throws Exception {
        try (Session session = driver.session()){
            session.run(dropproj);
            session.run(pl2proj);
        }
    }

    @Benchmark
    public void Q6PL2SCCTest() throws Exception {
        try (Session session = driver.session()){
            session.run(scc);
        }
    }

    @Benchmark
    public void Q7PL1ProjTest() throws Exception {
        try (Session session = driver.session()){
            session.run(dropproj);
            session.run(pl1proj);
        }
    }

    @Benchmark
    public void Q8PL1SCCTest() throws Exception {
        try (Session session = driver.session()){
            session.run(scc);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("the direction name in the resources path is need as first argument");
            System.exit(1);
        }
        String dir = args[0];
        int max = 200;
        for (int i = 10; i <= max; i+= 10) {
            Application.importGraph(dir, i);
            final Options opts = new OptionsBuilder()
                    .include(CypherBenchmarkTest.class.getSimpleName())
                    .forks(1)
                    .measurementIterations(5)
                    .warmupIterations(5)
                    .measurementTime(TimeValue.seconds(5))
                    .warmupTime(TimeValue.seconds(5))
                    .timeUnit(TimeUnit.MILLISECONDS)
                    .verbosity(VerboseMode.SILENT)
                    .build();
            Collection<RunResult> results = new Runner(opts).run();
            System.out.println("file:"+dir+" "+i);
            results.forEach(r -> {
                System.out.println(r.getPrimaryResult().getLabel() + "\t" + r.getPrimaryResult().getScore());
            });
            System.out.println();
        }
    }
}
