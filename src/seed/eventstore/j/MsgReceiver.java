package seed.eventstore.j;

public interface MsgReceiver {
    public void onReceive(Object message);
}
