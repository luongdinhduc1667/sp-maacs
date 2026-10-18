package vn.healthshare;

import org.junit.Test;
import vn.healthshare.sim.Benchmark;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

/** Real Type A1 operations at 64 bits verify the benchmark lifecycle without a full 517-bit experiment. */
public class BenchmarkProgressTest {
    @Test public void tenSamplesAcrossFivePointsFinishWithMonotonicOverallProgress(){
        List<Benchmark.Progress> events=new ArrayList<>();
        List<Benchmark.Sample> samples=new ArrayList<>();
        List<Benchmark.Result> results=new ArrayList<>();
        List<String> messages=new ArrayList<>();
        Benchmark.Report report=Benchmark.runDetailed(2,64,messages::add,samples::add,events::add,result->{
            // Results arrive as each point finishes, before work starts on the next point.
            Benchmark.Progress latest=events.getLast();
            assertEquals("K đúng",latest.phase());
            assertEquals(result.attributes(),latest.attributes());
            assertEquals((results.size()+1)*2,latest.completedMeasurements());
            results.add(result);
        });

        assertEquals(10,samples.size());assertEquals(5,results.size());
        assertEquals(samples,report.samples());assertEquals(results,report.results());
        assertTrue(samples.stream().allMatch(Benchmark.Sample::verified));
        assertEquals(List.of(5,10,15,20,25),results.stream().map(Benchmark.Result::attributes).toList());
        assertEquals(List.of(3,5,7,10,12),results.stream().map(Benchmark.Result::satisfyingAttributes).toList());
        assertEquals(1,report.warmups());

        int measured=0,warmups=0;
        for(Benchmark.Progress event:events){
            assertEquals(10,event.totalMeasurements());assertEquals(5,event.totalWarmups());assertEquals(15,event.totalSteps());
            assertTrue(event.completedMeasurements()>=measured);assertTrue(event.completedMeasurements()<=measured+1);
            assertTrue(event.completedWarmups()>=warmups);assertTrue(event.completedWarmups()<=warmups+1);
            assertEquals(event.completedMeasurements()+event.completedWarmups(),event.completedSteps());
            measured=event.completedMeasurements();warmups=event.completedWarmups();
        }
        Benchmark.Progress end=events.getLast();
        assertEquals("Hoàn tất",end.phase());assertEquals(10,end.completedMeasurements());assertEquals(5,end.completedWarmups());assertEquals(15,end.completedSteps());
        assertEquals("GlobalSetup",events.getFirst().phase());assertEquals(0,events.getFirst().pointIndex());
        assertTrue(events.getFirst().description().contains("0/10 lượt đo"));
        for(String phase:List.of("CASetup","AASetup 1/5","AASetup 5/5"))assertTrue(events.stream().anyMatch(e->e.phase().equals(phase)));

        List<Benchmark.Progress> measuredStarts=events.stream().filter(e->e.phase().equals("CAKeyGen")&&!e.warmup()).toList();
        assertEquals(10,measuredStarts.size());
        assertEquals(5,measuredStarts.get(1).attributes());assertEquals(2,measuredStarts.get(1).iteration());
        Benchmark.Progress nextPoint=measuredStarts.get(2);
        assertEquals(10,nextPoint.attributes());assertEquals(1,nextPoint.iteration());assertEquals(2,nextPoint.completedMeasurements());
        assertTrue(nextPoint.description().contains("Điểm 2/5"));assertTrue(nextPoint.description().contains("lượt 1/2"));assertTrue(nextPoint.description().contains("tổng 2/10"));
        for(String phase:List.of("CAKeyGen","AAKeyGen","Encrypt","Decrypt")){
            assertEquals(15,events.stream().filter(e->e.phase().equals(phase)).count());
            Benchmark.Progress first=events.stream().filter(e->e.phase().equals(phase)).findFirst().orElseThrow();
            assertTrue(first.warmup());assertEquals(0,first.completedMeasurements());assertEquals(0,first.completedWarmups());
        }
        assertEquals(5,events.stream().filter(e->e.phase().equals("K đúng")&&e.warmup()).count());
        assertTrue(messages.getFirst().contains("10 lượt đo"));assertTrue(messages.getLast().contains("Hoàn tất benchmark"));
    }

    @Test public void cancellationDuringEncryptDoesNotPublishUnfinishedPointOrStartAnother(){
        List<Benchmark.Progress> events=new ArrayList<>();
        List<Benchmark.Result> results=new ArrayList<>();
        List<Benchmark.Sample> samples=new ArrayList<>();
        assertFalse(Thread.currentThread().isInterrupted());
        try{
            Benchmark.runDetailed(2,64,message->{},samples::add,event->{
                events.add(event);
                if(event.phase().equals("Encrypt")&&!event.warmup())Thread.currentThread().interrupt();
            },results::add);
            fail("Cancellation must stop the finite benchmark immediately after the callback");
        }catch(CancellationException expected){
            assertTrue(results.isEmpty());assertTrue(samples.isEmpty());
            assertEquals("Encrypt",events.getLast().phase());
            assertEquals(1,events.getLast().completedWarmups());assertEquals(0,events.getLast().completedMeasurements());
            assertTrue(events.stream().allMatch(e->e.pointIndex()<=1));
            assertFalse(events.stream().anyMatch(e->e.phase().equals("Hoàn tất")));
        }finally{Thread.interrupted();}
    }
}
