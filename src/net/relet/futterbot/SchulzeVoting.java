/* SchulzeVoting.java -                  */
/* Licence: LGPL - by Thomas Hirsch 2008 */
/* Licence applies to this file only     */

package net.relet.futterbot;

import java.util.*;

public class SchulzeVoting<K> {
  private ArrayList<ArrayList<K>> ballots;
  private ArrayList<K> candidates;

  public SchulzeVoting() {}
  public void reset() {
    ballots=null;
    candidates=null;
  }

  public void addVote(ArrayList<K> ballot) {
    if (ballots==null) ballots=new ArrayList<ArrayList<K>>();
    if (candidates==null) candidates=new ArrayList<K>();
    ballots.add(ballot);
    for (int i=0; i<ballot.size(); i++) {
      K candidate = ballot.get(i);
      if (!candidates.contains(candidate)) candidates.add(candidate);
    }
  }

  public int[][] getPaths() {
    int size=candidates.size();
    if (size>65535) return null; //use linkedlists everywhere?
    /* count votes */
    int[][] defeats = new int[size][size];
    for (int i=0; i<ballots.size(); i++) {
      ArrayList<K> ballot = ballots.get(i);
      for (int j=0; j<ballot.size(); j++) {
        K choice=ballot.get(j);
        int pos   = candidates.indexOf(choice);
        for (int k=0; k<size; k++) {
          K comp=candidates.get(k);
          int place = ballot.indexOf(comp);
          if ((place<0) || (place>j)) {
            defeats[pos][k]++;
          }
        }
      }
    }
    /* calculate paths */
    int[][] paths = new int[size][size];
    for (int i=0; i<size; i++) {
      for (int j=0; j<size; j++) {
        if (i!=j) {
          if(defeats[i][j]>defeats[j][i]) paths[i][j]=defeats[i][j];
        }
      }
    }
    for (int i=0; i<size; i++) {
      for (int j=0; j<size; j++) {
        if (i!=j) {
          for (int k=0; k<size; k++) {
            if ((i!=k)&&(j!=k)) {
              paths[j][k] = Math.max(paths[j][k], Math.min( paths[j][i], paths[i][k]));
            }
          }
        }
      }
    }
    return paths;
  }

  public ArrayList<K> getWinners() {
    int size=candidates.size();
    int[][] paths = this.getPaths();
    /* determine winners */
    ArrayList<K> winners = (ArrayList<K>)candidates.clone();
    for (int i=0; i<size; i++) {
      for (int j=0; j<size; j++) {
        if (i!=j) {
          if (paths[j][i] > paths[i][j]) {
            winners.remove(candidates.get(i));
          }
        }
      }
    }
    return winners;
  }

  public ArrayList<ArrayList<K>> getRanks() {
    int size=candidates.size();
    int[][] paths = this.getPaths();
    /* determine all ranks */
    ArrayList<ArrayList<K>> ranks = new ArrayList<ArrayList<K>>();
    ArrayList<K> remaining = (ArrayList<K>)candidates.clone();
    while (remaining.size()>0) {
      ArrayList<K> winners = (ArrayList<K>)remaining.clone();
      for (int i=0; i<size; i++) {
        if (remaining.contains(candidates.get(i))) {
          for (int j=0; j<size; j++) {
            if (i!=j) {
              if (remaining.contains(candidates.get(j))) {
                if (paths[j][i] > paths[i][j]) {
                  winners.remove(candidates.get(i));
                }
              }
            }
          }
        }
      }
      ranks.add(winners);
      for (int i=0; i<winners.size(); i++) remaining.remove(winners.get(i));
    }
    return ranks;
  }

  public int countVotes() {
    return ballots.size();
  }

  /* some tests */
  public static void main(String[] args) {
    SchulzeVoting<String> v = new SchulzeVoting<String>();
    ArrayList<String> ballot=new ArrayList<String>();
    ballot.add("Correct");
    v.addVote(ballot);
    System.out.println("Tests:");
    //System.out.println("Single vote, single candidate: "+v.getWinners());
    System.out.println("Single vote, single candidate: "+v.getRanks());
    v.reset();
    ballot.add("Second");
    ballot.add("Third");
    v.addVote(ballot);
    //System.out.println("Single vote, three candidates: "+v.getWinners());
    System.out.println("Single vote, three candidates: "+v.getRanks());
    v.reset();
    ArrayList<String> ballot2=new ArrayList<String>();
    ballot2.add("Third");
    ballot2.add("Correct");
    ballot2.add("Second");
    ArrayList<String> ballot3=new ArrayList<String>();
    ballot3.add("Second");
    ballot3.add("Correct");
    ballot3.add("Third");
    v.addVote(ballot);
    v.addVote(ballot2);
    v.addVote(ballot3);
    //System.out.println("Three votes, three candidates: "+v.getWinners());
    System.out.println("Three votes, three candidates: "+v.getRanks());
     
  }
}
