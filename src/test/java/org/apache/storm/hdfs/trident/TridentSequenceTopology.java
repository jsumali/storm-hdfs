package org.apache.storm.hdfs.trident;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.StormTopology;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import org.apache.storm.hdfs.common.rotation.MoveFileAction;
import org.apache.storm.hdfs.trident.format.*;
import org.apache.storm.hdfs.trident.rotation.FileRotationPolicy;
import org.apache.storm.hdfs.trident.rotation.FileSizeRotationPolicy;
import storm.trident.Stream;
import storm.trident.TridentState;
import storm.trident.TridentTopology;
import storm.trident.operation.BaseFunction;
import storm.trident.operation.TridentCollector;
import storm.trident.state.StateFactory;
import storm.trident.tuple.TridentTuple;

public class TridentSequenceTopology {

    public static StormTopology buildTopology(){
        FixedBatchSpout spout = new FixedBatchSpout(new Fields("sentence", "key"), 1000, new Values("the cow jumped over the moon", 1l),
                new Values("the man went to the store and bought some candy", 2l), new Values("four score and seven years ago", 3l),
                new Values("how many apples can you eat", 4l), new Values("to be or not to be the person", 5l));
        spout.setCycle(true);

        TridentTopology topology = new TridentTopology();
        Stream stream = topology.newStream("spout1", spout);

        Fields hdfsFields = new Fields("sentence", "key");

        FileNameFormat fileNameFormat = new DefaultFileNameFormat()
                .withPath("/trident")
                .withPrefix("trident")
                .withExtension(".seq");

        FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(5.0f, FileSizeRotationPolicy.Units.MB);

        HdfsState.Options seqOpts = new HdfsState.SequenceFileOptions()
                .withFileNameFormat(fileNameFormat)
                .withSequenceFormat(new DefaultSequenceFormat("key", "data"))
                .withRotationPolicy(rotationPolicy)
                .withFsUrl("hdfs://localhost:54310")
                .addRotationAction(new MoveFileAction().toDestination("/dest2/"));

        StateFactory factory = new HdfsStateFactory().withOptions(seqOpts);

        TridentState state = stream
                .partitionPersist(factory, hdfsFields, new HdfsUpdater(), new Fields());

        return topology.build();
    }

    public static void main(String[] args) throws Exception {
        Config conf = new Config();
        conf.setMaxSpoutPending(5);
        if (args.length == 0) {
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("wordCounter", conf, buildTopology());
            Thread.sleep(120 * 1000);
        }
        else {
            conf.setNumWorkers(3);
            StormSubmitter.submitTopology(args[0], conf, buildTopology());
        }
    }
}
