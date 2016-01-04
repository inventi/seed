package seed.eventstore.j;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class DeathWatcher extends UntypedActor{

    private MsgReceiver receiver;

    public DeathWatcher(MsgReceiver receiver, ActorRef actor){
        this.receiver = receiver;
        getContext().watch(actor);
    }

    public void onReceive(Object message) throws Exception {
        this.receiver.onReceive(message);
    }

    public static Props props(ActorRef actor, MsgReceiver receiver){
        return Props.create(DeathWatcher.class, receiver, actor);
    }
}
