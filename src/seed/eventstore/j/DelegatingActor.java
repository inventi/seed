package seed.eventstore.j;

import akka.actor.Props;
import akka.actor.UntypedActor;

public class DelegatingActor extends UntypedActor{

    private MsgReceiver receiver;

    public DelegatingActor(MsgReceiver receiver){
        this.receiver = receiver;

    }

    public void onReceive(Object message) throws Exception {
        this.receiver.onReceive(message);
    }

    public static Props props(MsgReceiver receiver){
        return Props.create(DelegatingActor.class, receiver);
    }
}
