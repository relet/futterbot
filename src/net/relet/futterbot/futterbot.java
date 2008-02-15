/* futterbot.java -                      */
/* Licence: GPL - by Thomas Hirsch 2008  */
/* Licence applies to this file only     */

/** a multi-user voting jabber bot. 
    - register
    - around midday, you will be asked for your food preference and time preference
    - groups will be formed and informed about the results
**/

package net.relet.futterbot;

import java.util.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.*;


public class futterbot implements Runnable, RosterListener, ChatManagerListener, MessageListener, ConnectionListener {
  private final static String XMPP_USER = "xxx"; /* fill these in. TODO read from config file */
  private final static String XMPP_PASS = "xxx";
  private final static String XMPP_HOST = "xxx";
  
  public static void main(String[] args) {
    new futterbot();
  }

  HashMap<String, Chat> chats=new HashMap<String, Chat>(); /* a list of chats, one per user */
  HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>> votes = 
    new HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>>();
    /* channel - topic - user - preferences */
  HashMap<String, HashMap<String, String>> userPreferences=new HashMap<String, HashMap<String, String>>();
    /* user - key - value */
  HashMap<String, Presence> presences=new HashMap<String, Presence>();

  HashMap<String, Long> ignores=new HashMap<String, Long>();

  XMPPConnection con;
  ChatManager chatman;
  Presence presence;
  Roster roster;
  
  public static HashMap<String, String> cenrimbot;

  String hiliteGroup = null;

  static {
    cenrimbot = new HashMap<String, String>();
    cenrimbot.put("What colour does a banana have?", "yellow");
    cenrimbot.put("What is the value of PI, accurate to 2 digits?", "3.14");
    cenrimbot.put("What is the sum of 1 and 1?", "2");
    cenrimbot.put("How many fingers are there on one hand?","5");
    cenrimbot.put("How many legs does a dog have?", "4");
    cenrimbot.put("Is a wheel round or not (yes or no)?", "yes");
    cenrimbot.put("What is the first word of this sentence?", "What");
    cenrimbot.put("What is not a fruit: lettuce, apple or banana?", "lettuce");
    cenrimbot.put("What does a chicken lay?", "eggs");
  }

  public futterbot() {
    //todo: read a yaml config file

    /* Create a connection to the xmpp server. */
    try {
      System.out.println("futterbot 0.1 booting up.");
      ConnectionConfiguration conf=new ConnectionConfiguration(XMPP_HOST);
      conf.setReconnectionAllowed(true);
      con = new XMPPConnection(conf);
      con.connect();
      System.out.println(" * connected to "+con.getHost()+":"+con.getPort());
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(1);
    }
    try {
      /*System.out.println(con.getAccountManager().supportsAccountCreation());
      System.out.println(con.getAccountManager().getAccountInstructions());
      System.out.println(con.getAccountManager().getAccountAttributes());
      Map<String, String> atts=new HashMap<String, String>();
      atts.put("email", "thomas.hirsch@fokus.fraunhofer.de");
      atts.put("name", "CCNET futterbot!");
      con.getAccountManager().createAccount("futterbot", "pyfihu",atts);
      System.exit(1);*/
      con.login(XMPP_USER, XMPP_PASS);
      
    } catch (Exception ex) {
      ex.printStackTrace();
      System.err.println(" * failed to login");
      System.exit(1);
    }

    presence=new Presence(Presence.Type.available);
    presence.setStatus("at your service");
    con.sendPacket(presence);
    System.out.println(" * logged in and "+presence.getType());
      
    roster=con.getRoster();
    roster.addRosterListener(this);

    chatman = con.getChatManager();
    chatman.addChatListener(this);

    new Thread(this).start();
  }

  public void run() {
    
    try {
      while (true) {
        Thread.sleep(120000);
      }
    } catch (InterruptedException iex) {
      System.err.println("Someone interrupted my sleep.");
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    con.disconnect();
  }

/* RosterListener */
  public void entriesAdded(Collection<String> addresses) {
    System.out.println("roster.entriesAdded "+addresses);
  }
  public void entriesDeleted(Collection<String> addresses) {
    System.out.println("roster.entriesDeleted "+addresses);
  }
  public void entriesUpdated(Collection<String> addresses) {
    System.out.println("roster.entriesUpdated "+addresses);
  }
  public void presenceChanged(Presence presence) {
    System.out.println("roster.presenceChanged "+presence.getFrom()+" "+presence.getType()+" "+presence.getMode());
    String from = getJustUID(presence.getFrom());
    presences.put(from, presence);
  }

/* ConnectionListener */ 
 public void connectionClosed() {
   try {
     System.err.println("Disconnected. Reconnecting in 3.");
     Thread.sleep(3000);
     con.connect();
   } catch (Exception ex) {
     ex.printStackTrace();
   }
 }
 public void   connectionClosedOnError(Exception ex) {
   System.err.println("Disconnected.");
   ex.printStackTrace();
 }
 public void reconnectingIn(int seconds) {
   System.err.println("Notice: Reconnection in "+seconds+".");
 }
 public void reconnectionFailed(Exception e) {
   System.err.println("Notice: Reconnection failed.");
 }
 public void reconnectionSuccessful() {
   System.err.println("Notice: Reconnection successful.");
 }

/* ChatManagerListener */
  public void chatCreated(Chat chat, boolean createdLocally) {
    //System.out.println("Communication with "+chat.getParticipant()+" open.");
    chats.put(chat.getParticipant(),chat);
    chat.addMessageListener(this);
  }

/* MessageListener */
  public void processMessage(Chat chat, Message message) {
    //System.out.println(chat.getParticipant()+": "+message.toXML());
    if (message.getBody()!=null) {
      processBotMessage(chat.getParticipant(), message.getBody());
    }
  }

/* The actual bot code */

  private void sendBotMessage(String user, String message) {
    Chat chat = chats.get(user);
    if (chat==null) {
      chat = chatman.createChat(user, this);
    }

    try {
      chat.sendMessage(message);
    } catch (XMPPException xex) {
      xex.printStackTrace();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private synchronized void processBotMessage(String user, String message) {
    processBotMessage(user, message, null);
  }
  private synchronized void processBotMessage(String user, String message, String prequel) {
    user = getJustUID(user);
    if ((message.indexOf("I'm using a test to determine")>-1)||(message.indexOf("You answered the question incorrectly")>-1)) {
      String question = message.substring(message.indexOf("\n")+1);
      String answer = cenrimbot.get(question);
      if (answer != null) {
        sendBotMessage(user, answer);
        return;
      } else {
        System.err.println("Could not find an answer to the question: --"+question+"--");
      }
    }
    message = message.replaceAll("<.*>","");
    System.out.println("processing "+user+" "+message);
    String[] msg=message.toLowerCase().split("\\s+");
    String reply="If you read this, someone stole my reply message.";
    int sequel = -1;

    if (msg.length==0) {
      sendBotMessage(user, "You seem so quiet today...");
      return;
    }
    if (msg[0].equals("help")||msg[0].equals("usage")||msg[0].equals("wer")||msg[0].equals("who")||msg[0].equals("about")) {
      reply = botPrintUsage();
      sequel = 1;
    } else if (msg[0].equals("test")) {
      reply = "pong.\r\nI am testing you, you know.";
      sequel = 1;
    } else if (msg[0].equals("groups")) {
      reply = botPrintGroups(user);
      sequel = 1;
    } else if (msg[0].equals("ignore")) {
      long duration = 18 * 3600 * 1000;
      try {
        duration = Long.parseLong(msg[1]) * 3600 * 1000;
      } catch (Exception ex) {} //nope.
      ignores.put(user, new Long(System.currentTimeMillis()+duration));
      reply = "Yeah, ok. I'm gonna bug somebuddy else for the next "+(duration/3600000)+" hours.";
    } else if (msg[0].equals("join")) {
      if (msg.length<2) {
        reply = "Oh, oh, you're in the army now.\nIf you'd rather want to join another group, you'd have to name it.";
      } else {
        reply = botJoinGroup(user, msg[1]);
        sequel = 2;
      }
    } else if (msg[0].equals("leave")) {
      if (msg.length<2) {
        reply = "To take a leave, please contact human resources.\nIf you'd rather want to leave a group, you'd have to name it.";
      } else {
        reply = botLeaveGroup(user, msg[1]);
        sequel = 2;
      }
    } else if (msg[0].equals("vote")) {
      reply = botVote(user, msg);
    } else if (msg[0].equals("sudo")) {
      reply = botSudo(user, msg);
    } else if (msg[0].equals("nick")) {
      String nick=(msg.length>=2)?msg[1]:null;
      reply = botNick(user, nick);
      sequel = (msg.length>=2)?2:-1;
    } else if (msg[0].equals("say")) {
      reply = botSay(user, msg);
    } else if (msg[0].equals("votes")||msg[0].equals("result")||msg[0].equals("tally")) {
      String topic = (msg.length>1)?msg[1]:null;
      reply = botResult(user, topic, false);
      sequel = 1;
    } else if (msg[0].equals("voters")) {
      String topic = (msg.length>1)?msg[1]:null;
      reply = botResult(user, topic, true);
      sequel = (msg.length>1)?2:1;
    } else if (msg[0].equals("in")) {
      if (msg.length<2) {
        reply = "Careful with that return key.";
      } else {
        reply = botIn(user, msg[1]);
        sequel = 2;
      }
    } else if (msg[0].equals("on")) {
      if (msg.length<2) {
        reply = "Careful with that return key.";
      } else {
        reply = botAbout(user, msg[1]);
        sequel = 2;
      }
    } else if (msg[0].equals("members")) {
      String group = null;
      if (msg.length>=2) {
        group = msg[1];
        sequel = 2;
      } else {
        sequel = 1;
        HashMap<String, String> prefs = userPreferences.get(user);
        if (prefs != null) {
          String in = prefs.get("in");
          if (in!=null) group=in;
        }
      }
      if (group==null) {
        RosterEntry entry = roster.getEntry(user);
        if (entry!=null) {
          Collection<RosterGroup> groups=entry.getGroups();
          if (groups!=null) {
            if (!groups.isEmpty()) {
              group=groups.iterator().next().getName();
            } else {
              reply = "You are in no interest group, so you'll have to specify one.";
            }
          } else {
            reply = "I could not find any interest group.";
          }
        } else {
          reply = "You are not on my list.\nIf you are interested in eating with other people, type 'invite me'.\n"
                 +"If you just want to know more about this service, type 'about'";
        }
      }
      if (group!=null) reply = botPrintMembers(group);
    } else if (msg[0].equals("invite")) {
      if (msg.length<2) {
        reply = "Whom shall I invite? You or some other jabber ID?";
      } else {
        if (msg[1].equals("me")) msg[1]=user;
        String group = (msg.length>=3)?msg[2]:null;
        reply = botAddUserToGroup(msg[1], group);
        sequel = (msg.length>=3)?3:2;
      }
    } else {
      reply = "I did not quite get you..\nSend 'about' for information about me and the commands you can give me.";
    }
    System.out.println(" * reply sent.");
    if (prequel!=null) reply=prequel+"\n"+reply;
    if ((sequel > -1) && (msg.length>sequel)) {
      String remainder = "";
      for (int i=sequel; i<msg.length; i++)
        remainder += msg[i]+" ";
      processBotMessage(user, remainder, reply);
    } else {
      sendBotMessage(user, reply);
    }
  }

/* Any action the bot can take */
  private String botPrintUsage() {
    return "We are futterbot. You will be assimilated.\n"
          +"We help to organize joint lunches, but also any other joint activity.\n\n"
          +"The following commands will help you with that:\n"
          +"* usage|help|about          \t- prints these instructions.\n"
          +"* invite {me|userid} [group]\t- invites a jabber user (or yourself) to the service.\n"
          +"* nick [alias]              \t- sets your nickname. I will try to hide your jabber id in output.\n"
          +"* groups                    \t- lists all available interest groups.\n"
          +"* members [group]           \t- lists all members of your current group (or specified group).\n"
          +"* join [group]              \t- join another group, or create one.\n"
          +"* leave [group]             \t- leave the given group.\n"
          +"* in [group]                \t- specify a group to vote in.\n"
          +"* on [topic]                \t- specify a topic to vote about.\n"
          +"* vote [best [...]]         \t- example: \"vote pizza thai\" (in order of preference).\n"
          +"* say [something]           \t- broadcast to your (current) group.\n"
          +"* votes|result|tally [topic]\t- print the current result of all given votes (on given topic or all, in current group).\n"
          +"* voters [topic]            \t- print out a participant list of the current vote (on given topic or last specified, in group).\n"
          +"* ignore [hours]            \t- the server will not send you notifications for a given number of hours. Defaults to 18.\n"
         +"\nBe nice and enjoy the service.";
  }

  private String botSudo(String sender, String[] msg) {
    System.err.println(sender+" sudoed:");
    try {
      System.err.println("\t"+msg[0]+" "+msg[1]+" "+msg[2]);
      if (msg[1].equals("rmug")) {
        RosterGroup group=(msg.length>=4)?roster.getGroup(msg[3]):roster.getGroup("");
        RosterEntry user =roster.getEntry(msg[2]);
        group.removeEntry(user);
      } else if (msg[1].equals("adug")) {
        RosterGroup group=roster.getGroup(msg[3]);
        if (group==null) group=roster.createGroup(msg[3]);
        RosterEntry user =roster.getEntry(msg[2]);
        if (user==null) {
          roster.createEntry(msg[2], msg[2], null);
          user=roster.getEntry(msg[2]);
        }
        group.addEntry(user);
      } else if (msg[1].equals("rmu")) {
        RosterEntry user =roster.getEntry(msg[2]);
        roster.removeEntry(user);
      } else if (msg[1].equals("rstg")) {
        votes.remove(msg[2]);
        if (msg[2].equals(hiliteGroup)) updateHilite();
      } else if (msg[1].equals("hilite")) {
        hiliteGroup=msg[2];
        updateHilite();
      } else if (msg[1].equals("send")) {
        String message="";
        for (int i=3;i<msg.length;i++)
          message+=msg[i]+" ";
        sendBotMessage(msg[2], message);
      }
    } catch (Exception ex) {
    }
    return "I did not quite get you..\nSend 'about' for information about me and the commands you can give me.";
  }

  private String botNick(String user, String alias) {
    RosterEntry entry=roster.getEntry(user);
    if (entry==null) return "I don't know you anyway. Try 'invite me' or 'about'.";
    entry.setName(alias);
    if (alias==null) {
      return "Ok, your ID is now visible again.";
    } else {
      return "Enchantay, "+alias+".";
    }
  }


  private String botIn(String user, String sgroup) {
    RosterGroup group=roster.getGroup(sgroup);
    if (group==null) return "That group is not known to me.";
    if (userPreferences.get(user)==null) {
      userPreferences.put(user, new HashMap<String, String>());
    }
    userPreferences.get(user).put("in",sgroup);
    return "Ok. Selected group is now "+sgroup;  
  }

  private String botAbout(String user, String about) {
    if (userPreferences.get(user)==null) 
      userPreferences.put(user, new HashMap<String, String>());
    userPreferences.get(user).put("on",about);
    return "Ok.";  
  }

  private String botVote(String user, String[] commands) {
    HashMap<String, String> prefs = userPreferences.get(user);
    if (prefs == null) return "Please specify a topic with 'on' first.";
    String in = prefs.get("in");
    if (in == null) {
      RosterEntry entry = roster.getEntry(user);
      if (entry==null) return "I don't know you. Try 'invite me' or 'about'";
      Iterator<RosterGroup> groups=entry.getGroups().iterator();
      if (!groups.hasNext()) return "Join a group first. No one cares for your opinion otherwise.";
      RosterGroup group=groups.next();
      in=group.getName();
    }
    String about = prefs.get("on");
    if (about==null) return "Please specify a topic with 'on' first.";
    if (votes==null) votes=new HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>>();
    if (votes.get(in)==null) votes.put(in, new HashMap<String, HashMap<String, ArrayList<String>>>());
    if (votes.get(in).get(about)==null) votes.get(in).put(about, new HashMap<String, ArrayList<String>>());
    ArrayList<String> choices=new ArrayList<String>();
    for (int i=1;i<commands.length;i++) choices.add(commands[i]);
    votes.get(in).get(about).put(user, choices);
    if (in.equals(hiliteGroup)) updateHilite();
    return "Your selection has been registered.";
  }

  private void updateHilite() {
    if (hiliteGroup==null) {
      presence.setStatus("at your service.");
    } else {
      HashMap<String, HashMap<String, ArrayList<String>>> result = votes.get(hiliteGroup);
      if (result==null) {
        presence.setStatus("at your service.\n");
      } else {
        Iterator<String> topics = result.keySet().iterator();
        String status="";
        while (topics.hasNext()) {
          String topic=topics.next();
          HashMap<String, Integer> tally=new HashMap<String, Integer>();
          HashMap<String, ArrayList<String>> userChoices=result.get(topic);
          Iterator<String> users=userChoices.keySet().iterator();
          SchulzeVoting<String> v = new SchulzeVoting<String>();
          while (users.hasNext()) {
            String user=users.next();
            ArrayList<String> choices=userChoices.get(user);
            v.addVote(choices);
          }
          ArrayList<String> winners=v.getWinners();
          status+="["+topic+"|"+userChoices.keySet().size()+"] ";
          for (int i=0;i<winners.size();i++) {
            status+=winners.get(i)+" ";
          }
        }
        presence.setStatus(status);
      }
    }
    con.sendPacket(presence);
  }

  private String botResult(String user, String topic, boolean voters) {
    HashMap<String, String> prefs = userPreferences.get(user);
    String in = (prefs==null)?null:prefs.get("in");
    if (in == null) {
      RosterEntry entry = roster.getEntry(user);
      if (entry==null) return "I don't know you. Try 'invite me' or 'about'";
      Iterator<RosterGroup> groups=entry.getGroups().iterator();
      if (!groups.hasNext()) return "You're not even in any group. So why should I tell you anything?";
      RosterGroup group=groups.next();
      in=group.getName();
    }
    if (!votes.containsKey(in)) return "There have not been any votes in the group '"+in+"'.";
    HashMap<String, HashMap<String, ArrayList<String>>> result = votes.get(in);
    return botTally(in, result, topic, voters); 
  }

  private String botSay(String user, String[] msg) {
    HashMap<String, String> prefs = userPreferences.get(user);
    String in = (prefs==null)?null:prefs.get("in");
    RosterEntry entry = roster.getEntry(user);
    if (in == null) {
      if (entry==null) return "I don't know you. Try 'invite me' or 'about'";
      Iterator<RosterGroup> groups=entry.getGroups().iterator();
      if (!groups.hasNext()) return "You're not even in any group. So whom do you want to talk to?";
      RosterGroup group=groups.next();
      in=group.getName();
    }
    RosterGroup group=roster.getGroup(in);
    if (group==null) return "This group does not exist.";
    Iterator<RosterEntry> members=group.getEntries().iterator();
    String message=getNameOrUser(entry)+"@"+group.getName()+": ";
    for (int i=1;i<msg.length;i++)
      message+=msg[i]+" ";
    String good="";
    int bad=0;
    String reply="Ok, I notified the following people:\n";
    while (members.hasNext()) {
      entry=members.next();
      String member=getJustUID(entry.getUser());
      String name=getNameOrUser(entry);
      Long skip = ignores.get(member);
      if ((skip!=null)&&(skip.longValue()>System.currentTimeMillis())) {
        bad+=1;
      } else {
        Presence presence=presences.get(member);
        if (presence==null) presence=roster.getPresence(member);
        if (presence==null) {
          bad+=1;
        } else if (presence.getType()!=Presence.Type.available) {
          bad+=1;
        } else if (presence.getMode()==Presence.Mode.dnd) {
          bad+=1;
        } else if (presence.getMode()==Presence.Mode.xa) {
          bad+=1;
        } else {
          sendBotMessage(member, message);
          good+=name+", ";
        }
      }
    }
    return reply+good+" -- skipped "+bad+".";
  }

  private String beautifyArrayList(ArrayList<String> list) {
    String nruter="";
    int count = list.size();
    for (int i=0;i<count;i++) {
      nruter+=list.get(i);
      if (i<count-1) nruter+=", ";
    }
    if (count>1) nruter+=" (tied)";
    return nruter;
  }

  private String botTally(String group, HashMap<String, HashMap<String, ArrayList<String>>> result, String showtopic, boolean listVoters) {
    String reply="In the name of the People of '"+group+"':\n";
    String voters="The following group members have voted -";
    Iterator<String> topics = result.keySet().iterator();
    while (topics.hasNext()) {
      String topic=topics.next();
      if ((showtopic!=null) && (!showtopic.equals(topic))) continue;
      reply+="== The '"+topic+"' issue is decided as such: ";
      voters+="\non '"+topic+"': \n";
      HashMap<String, Integer> tally=new HashMap<String, Integer>();
      HashMap<String, ArrayList<String>> userChoices=result.get(topic);
      Iterator<String> users=userChoices.keySet().iterator();
      SchulzeVoting<String> v = new SchulzeVoting<String>();
      while (users.hasNext()) {
        String user=users.next();
        if (listVoters) {
          voters+=getNameOrUser(roster.getEntry(user))+" ";
        } else {
          ArrayList<String> choices=userChoices.get(user);
          v.addVote(choices);
        }
      }
      if (listVoters) return voters;
      ArrayList<ArrayList<String>> ranks=v.getRanks();
      reply+="("+v.countVotes()+" People have an opinion on this). ==\n";
      for (int i=0;i<ranks.size();i++) {
        reply+="  Rank #"+(i+1)+":\t"+beautifyArrayList(ranks.get(i))+"\n";
      }
    }
    reply+="(rankings are calculated based on the Schulze method. The finest in democracy since 1997.)";
    return reply;
  }

  private String botPrintGroups(String user) {
    String reply = "We got the following interest groups: ";
    
    HashMap<String, String> prefs = userPreferences.get(user);
    String in = null;
    if (prefs != null) {
      in = prefs.get("in");
    }
    
    Iterator<RosterGroup> groups=roster.getGroups().iterator();
    while (groups.hasNext()) {
      RosterGroup group=groups.next();
      String name = group.getName();
      if ((in!=null) && (in.equals(name))) {
        name = name + " --SELECTED--";
      }
      reply+="\n* "+name+" ("+group.getEntryCount()+" participants)";
    }
    return reply;
  }

  private String botJoinGroup(String user, String sgroup) {
    if (!roster.contains(user)) return "I don't know you yet. Try 'invite me' or 'about'.";
    RosterEntry entry=roster.getEntry(user);
    RosterGroup group=roster.getGroup(sgroup);
    if (group==null) group=roster.createGroup(sgroup);
    try {
      group.addEntry(entry);
      return "Ok, you're now officially with that '"+sgroup+"' bunch.";
    } catch (XMPPException xex) {
      xex.printStackTrace();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return "Whoa! That didn't work out, somehow.";
  }
  private String botLeaveGroup(String user, String sgroup) {
    RosterEntry entry=roster.getEntry(user);
    if (entry==null) return "I don't even know you yet. Try 'invite me' or 'about'.";
    RosterGroup group=roster.getGroup(sgroup);
    if (group==null) return "Since the group '"+sgroup+"' doesn't exist, you can hardly be in there, can you?";
    try {
      group.removeEntry(entry);
      return "Ok, you're now officially done with these '"+sgroup+"' folks.";
    } catch (XMPPException xex) {
      xex.printStackTrace();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return "Whoa! That didn't work out, somehow.";
  }

  private String botPrintMembers(String sgroup) {
    /* This command might have to be disabled for privacy reasons */
    RosterGroup group=roster.getGroup(sgroup);
    String reply="These people are in "+sgroup+":";
    if (group==null) {
      return "I don't know the group '"+sgroup+"' yet.";
    } else {
      Iterator<RosterEntry> members=group.getEntries().iterator();
      while (members.hasNext()) {
        RosterEntry entry=members.next();
        String juid=getJustUID(entry.getUser());
        reply+="\n  "+getNameOrUser(entry);
        if (ignores.get(juid)!=null) {
          if (ignores.get(juid).longValue()>System.currentTimeMillis()) {
            reply+=" (ignored)";
            continue;
          }
        }
        Presence presence=presences.get(juid);
        if (presence==null) presence=roster.getPresence(juid);
        String status=presence.getType()==Presence.Type.unavailable?"---":presence.getStatus();
        if (status==null) status=presence.getMode()==null?null:presence.getMode().toString();
        if (status==null) status="available";
        reply+=" ("+status+"), ";
      }
    }
    return reply;
  }

  private String getNameOrUser(RosterEntry entry) {
    if (entry==null) return "-?-";
    String nruter=entry.getName();
    if (nruter==null) nruter=entry.getUser();
    return nruter;
  }
  private String getJustUID(String resourceuid) {
    return resourceuid.split("/")[0];
  } 

  private String botAddUserToGroup(String user, String group) {
    try {
      if (roster.contains(user)) {
        RosterEntry entry=roster.getEntry(user);
        RosterGroup rgroup=roster.getGroup(group);
        if (rgroup==null) return "I don't know that group.";
        if (entry.getGroups().contains(rgroup)) {
          return "That one is already in that group.";
        } else {
          String username = getNameOrUser(entry);
          sendBotMessage(username, "You're being invited to join group '"+group+"'. Type 'join "+group+"' to do so.");
          return "Ok, I sent an invitation note.";
        }
      }
      String[] groups = (group==null)?null:new String[]{group};
      roster.createEntry(user, user, groups);
      String reply = "Ok, I invited "+user+" to join our round table. I'll just have to await authorization.";
      if (groups!=null) reply+="\nIf e accepts, "+user+" will be eating with the '"+group+"' group, unless e chooses otherwise.";
      return reply;
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return "No, well, that didn't work out.";
  }
}
