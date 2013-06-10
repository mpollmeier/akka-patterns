akka-patterns
=============

Work Pulling Pattern
=============
Pattern to prevent mailbox overflow, throttle and distribute work. 

Benefits:
1) No more out of memory issues because of too many messages in the mailboxes.
2) We only create as much work as is actually needed - Kanban style! This also means minimal liability for the workers. If a node goes down, we only loose the messages that were currently worked on.
3) Completely non-blocking, fully utilise your resources.
4) The actors involved can still receive other messages, e.g. to stop them.
5) Distribute work across the cluster with ease. Fully utilize all your boxes so you don't need more than necessary.  
6) Scale up and down dynamically by simply adding more nodes to the cluster during the execution of an epic. The workers on that node will happily join in with the other workers and start kicking off work to speed up the execution. Afterwards you can shut it down again to save some pesos. Cloud computing at it's best!
7) It's not a polling model where you have the overhead that workers regularly ask for work. They only ask when there's actually work available.
8) Easy to use: simply instantiate a master, give it a name and implement doWork on the worker. 

Getting started:
All it takes is to instantiate a master node, create an epic and workers that implement doWork. See WorkPullingPatternEmailScenarioSpec for a working example. 

Feel free to clone / fork / steal / pull-request / whatever it ;)

For a full description check out my [blog post](http://www.michaelpollmeier.com/akka-work-pulling-pattern/)
