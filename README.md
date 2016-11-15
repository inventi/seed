# seed
Having implemented couple of CQRS projects in Java, I always wanted to try out how that would work in Clojure.

This is working proof of concept with some sample aggreagate and simple process.
The example is from finance - Account as an aggreate and Transfer as a process.

I use EventStore for events, which is the best for event sourcing, hands down. Huge time saver. Process/saga state is also stored in EventStore as events.Automat is used as a state machine for process manager. I wanted to have the simplest possible process, so ended up with state machine, listening to events and returning commands. I have yet to see is it enough for reall-world scenario, but for now I really like how it looks.

## Development
You need to do few things before you can try this out:

* Make sure you have [boot](https://github.com/boot-clj/boot) installed
* You will also need [docker](https://www.docker.com/)
* Start eventstore container, from ``docker`` folder with ``docker-compose up -d``
* Change config. There is hardoced config in clojure for now, so you have to change your docker host IP manually (if its not localhost) in ``seed.core.config`` namespace.

Finally you can start the repl with ``boot dev``.
There are few functions available to start with:
* boot.user/start - starts all services.
* boot.user/reset - reloads namepsaces, use it if you want to change aggregate/commands functions.
* seed.accounts.api/test-accounts - create two accounts and put some initial money into first one.
* seed.accounts.api/transfer-money - transfer money from one account to another. This will trigger transfer process

Good luck, and feedback is always welcome! 






