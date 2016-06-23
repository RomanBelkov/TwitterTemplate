/**
 * Created by rb on 20.06.16.
 */

import rx.Observable;
import twitter4j.*;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;

enum RoboAction {Forward, Left, Right, Backward, Stop};

public class Program {

    private static volatile HashMap<RoboAction, Integer> ActionFrequencyMap = new HashMap<>(6);
    private static PowerMotor leftMotor = null;
    private static PowerMotor rightMotor = null;

    private static RoboAction CountStats() {
        Map.Entry<RoboAction, Integer> maxEntry = null;

        for (Map.Entry<RoboAction, Integer> entry : ActionFrequencyMap.entrySet())
        {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0)
            {
                maxEntry = entry;
            }
        }

        if (maxEntry == null) return null;
        if (ActionFrequencyMap.get(RoboAction.Stop).compareTo(maxEntry.getValue()) == 0) return RoboAction.Stop;
        return maxEntry.getKey();
    }

    private static void FlushValues() {
        for (RoboAction a : RoboAction.values()) {
            ActionFrequencyMap.put(a, 0);
        }
    }

    private static void Act(RoboAction action) throws InterruptedException {
        if (action.equals(RoboAction.Forward)) {
            leftMotor.setPower(100); rightMotor.setPower(100); Thread.sleep(2000); leftMotor.stop(); rightMotor.stop();
        }
        else if (action.equals(RoboAction.Left)) {
            rightMotor.setPower(100); Thread.sleep(1000); rightMotor.stop();
        }
        else if (action.equals(RoboAction.Right)) {
            leftMotor.setPower(100); Thread.sleep(1000); leftMotor.stop();
        }
        else if (action.equals(RoboAction.Backward)) {
            leftMotor.setPower(-100); rightMotor.setPower(-100); Thread.sleep(2000); leftMotor.stop(); rightMotor.stop();
        }
    }

    private static void Subscription() {

        System.out.println("BEFORE");

        for (RoboAction a : RoboAction.values()) {
            System.out.println(ActionFrequencyMap.get(a));
        }

        try {
            Act(CountStats());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        FlushValues();

        System.out.println("AFTER");

        for (RoboAction a : RoboAction.values()) {
            System.out.println(ActionFrequencyMap.get(a));
        }
    }

    public static void main(String[] args) throws TwitterException {

        //ArrayList<RoboAction> CurrentActionList = new ArrayList<>(20);
        //HashMap<RoboAction, Integer> ActionFrequencyMap = new HashMap<>(6);

        I2cTrik.INSTANCE.open();
        I2cTrik.INSTANCE.writeWord(0x12, 0x1000);
        I2cTrik.INSTANCE.writeWord(0x13, 0x1000);

        leftMotor  = new PowerMotor(MotorPorts.M2);
        rightMotor = new PowerMotor(MotorPorts.M4);

        for (RoboAction a : RoboAction.values()) {
            ActionFrequencyMap.putIfAbsent(a, 0);
        }

        String consumerKey = "62RySUpnQ0UkqKPMUpJoKUdGS";
        String consumerSecret = "39mOfPNVniJQ9DWPMchwwxi6WxVnbwRh8gPrngT9cZOWFSvrc0";
        String accessToken = "3974223010-ukREGh48Da8QfuepqHb200J78oCvwk62PZWZ8RC";
        String accessTokenSecret = "DUUf5RVessg1q8D5DqM81f8UP4YHtB81j65n2vX19RfsD";

        AccessToken token = new AccessToken(accessToken, accessTokenSecret);
        Configuration config = new ConfigurationBuilder()
                .setOAuthAccessToken(token.getToken())
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setOAuthAccessTokenSecret(accessTokenSecret)
                .build();

        TwitterStream twitterStream = new TwitterStreamFactory(config).getInstance();
        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                //System.out.println("@" + status.getUser().getScreenName() + " - " + status.getText());
                String text = status.getText();

                for (RoboAction a : RoboAction.values()) {
                    if (text.contains(a.toString())) {
                        ActionFrequencyMap.put(a, ActionFrequencyMap.get(a) + 1);
                    }
                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {
            }

            @Override
            public void onStallWarning(StallWarning warning) {
            }

            @Override
            public void onException(Exception ex) {
                ex.printStackTrace();
            }
        };
        twitterStream.addListener(listener);

        //tracking by keywords. Tracking by id can also be added
        FilterQuery fq = new FilterQuery();
        String keywords[] = {"TRIKtest"};

        fq.track(keywords);
        twitterStream.filter(fq);

        Observable.interval(20, TimeUnit.SECONDS).subscribe(x -> Subscription());
    }
}