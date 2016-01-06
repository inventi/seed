package seed.eventstore.j;

import akka.actor.UntypedActor;

public interface MsgReceiver {
    public void onInit(UntypedActor actor);
    public void onReceive(Object message);
}
