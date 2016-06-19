package seed.core.eventstore.j;

import akka.dispatch.OnSuccess;
import scala.util.Success;

/**
 * Created by zhilvis on 16-06-18.
 */
public class DelegatingOnSuccess extends OnSuccess {

    private MsgReceiver receiver;

    public DelegatingOnSuccess(MsgReceiver receiver){
        this.receiver = receiver;
        this.receiver.onInit(null);
    }

    @Override
    public void onSuccess(Object o) throws Throwable {
        this.receiver.onReceive(o);
    }
}
