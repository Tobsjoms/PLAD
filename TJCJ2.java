package org.rgrig;

import java.util.*;
import java.net.URI;
import java.util.stream.Collectors;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;




class Client extends WebSocketClient {
    enum State {
        START, IN_PROGRESS,
    }

    State state = State.START;
    String kentId;
    String token;
    String bad;
    String good;
    String choice;
    String lastChoice;
    int asked;

    // Good State
    HashMap<String, List<String>> original_dag;
    HashMap<String, List<String>> dag;
    HashMap<String, Integer> parent_counts;
    HashMap<String, Set<String>> parent_lookup;
    Set<String> tails = new HashSet<>();

    // Required Bullshit
    Client(final URI server, final String kentId, final String token) {
        super(server);
        this.kentId = kentId;
        this.token = token;
        this.asked = 0;
    }

    private void parse_dag(JSONObject message) {
        System.out.println("---------------------------------------------");
        System.out.println("Entering parse_dag");
        long start = System.currentTimeMillis();
        JSONArray dag = message.getJSONObject("Repo").getJSONArray("dag");
        System.out.println("Problem Name: " + message.getJSONObject("Repo").getString("name"));



        // Our Data
        HashMap<String, List<String>> result = new HashMap<>();
        ArrayList<String> tails = new ArrayList<String>();
        HashMap<String, Integer> parent_counts = new HashMap<>();
        HashMap<String, Set<String>> parent_lookup = new HashMap<>();


        // Map over commits to extract information.
        dag.forEach((item) -> {
            // Get Commit and its parents.
            JSONArray arr = (JSONArray)item;
            String child = arr.getString(0);
            ArrayList<String> parents = new ArrayList<String>();

            // First find all parents of this child.
            arr.getJSONArray(1).forEach((parent) -> {
                parents.add((String)parent);
            });

            // For each parent, add a lookup to this child.
            parents.forEach((parent) -> {
                if (!parent_lookup.containsKey(parent)) {
                    parent_lookup.put(parent, new HashSet<>());
                }

                parent_lookup.get(parent).add(child);
            });

            result.put(child, parents);
            parent_counts.put(child, parents.size());

        });

        // Store results
        this.parent_counts = parent_counts;
        this.parent_lookup = parent_lookup;
        this.original_dag = result;
        this.dag = (HashMap<String, List<String>>) this.original_dag.clone();
        System.out.println("Finished: " + ((System.currentTimeMillis() - start) / 1000.) + "s");
        System.out.println("");
    }

    private void purge_invalid_commits(String good, String bad) {
        System.out.println("Entering purge");
        long start = System.currentTimeMillis();
        Set<String> toFollow = new HashSet<String>();
        Set<String> keep = new HashSet<String>();

        // Check Each Heads Parent
        toFollow.add(bad);


        while (!toFollow.isEmpty()) {
            Set<String> newFollows = new HashSet<String>();
            toFollow.forEach((commit) -> {
                List<String> parents = dag.get(commit);
                if (!commit.equals(good) && parents != null) {
                    keep.add(commit);
                    newFollows.addAll(dag.get(commit));
                }
            });
            toFollow = newFollows;
        }

        Set<String> remove = new HashSet<>(dag.keySet());
        remove.removeAll(keep);
        remove.remove(good);
        remove.remove(bad);

        this.dag.keySet().removeAll(remove);
        this.parent_counts.keySet().removeAll(remove);

        this.tails.clear();
        this.dag.keySet().forEach((key) -> {
            List<String> parents = this.dag.get(key);
            if (parents == null) {
                this.tails.add(key);
            } else {
                boolean validParents = true;
                for (String parent : parents) {
                    if (this.parent_counts.get(parent) != null) {
                        validParents = false;
                    }
                }

                if (validParents) {
                    this.tails.add(key);
                }
            }
        });
        System.out.println("Finished: " + ((System.currentTimeMillis() - start) / 1000.) + "s");
        System.out.println("");
    }

    public void render_dag() {
        System.out.println("Entering render");
        long start = System.currentTimeMillis();

        System.out.println("Dag Size:");
        System.out.println(this.dag.size());
        System.out.println("Line 200");
        this.dag.keySet().forEach((commit) -> {
            String id = (String)commit;
            List<String> parents = this.dag.get(id);
            System.out.print("ID: ");
            System.out.print(id.length() > 5 ? id.substring(0, 5) : id);
            System.out.print("(");
            System.out.print(this.parent_counts.get(id));
            System.out.print(") -- ");
            parents.forEach((parent) -> {
                System.out.print(parent);
                System.out.print(", " );
            });
            System.out.print("\n");
        });
        System.out.println("Finished: " + ((System.currentTimeMillis() - start) / 1000.) + "s");

    }

    public String get_target_commit() {
        return null;
    }
    int problemCount = 0;
    public void assign_parents() {
        System.out.println("Entering assign Ps");
        long start = System.currentTimeMillis();
        this.dag.keySet().forEach((key) -> {
            this.parent_counts.put(key, 1);
        });

        Set<String> toVisit = new HashSet<String>(tails);
        HashMap<String, Integer> visited = new HashMap<>();
        while (!toVisit.isEmpty()) {

            Set<String> has_parent = new HashSet<>();

            toVisit.forEach((tail) -> {

                if (!visited.containsKey(tail)) {
                    visited.put(tail, 0);
                }
                visited.put(tail, visited.get(tail) + 1);


                List<String> parents = this.dag.get(tail);
                int sum_of_parents = 0;
                if (parents != null) {
                    sum_of_parents = parents
                            .stream()
                            .reduce(0, (l, r) -> {
                                Integer current = this.parent_counts.get(r);
                                current = current == null ? 0 : current;
                                return l + current;
                            }, Integer::sum);
                }

                this.parent_counts.put(
                        tail,
                        1 + sum_of_parents
                );

                Set<String> children = this.parent_lookup.containsKey(tail)
                        ? this.parent_lookup.get(tail)
                        : new HashSet<>();

                has_parent.addAll(
                        children
                                .stream()
                                .filter(c -> this.dag.containsKey(c))
                                .collect(Collectors.toSet())
                );
            });

            toVisit = has_parent;
        }

        System.out.println("Finished: " + ((System.currentTimeMillis() - start) / 1000.) + "s");

    }

    public String assign_minimums(String bad) {
        System.out.println("Entering assign_min");
        long start = System.currentTimeMillis();
        //      System.out.println("Line 271");
        Integer N = this.parent_counts.get(bad);
        String[] target = { null };
        this.parent_counts.keySet().forEach((key) -> {
            Integer X = this.parent_counts.get(key);
            X = Math.min(X, (N - X));
            this.parent_counts.put(key, X);

            if(target[0] == null) {
                target[0] = key;
            }

            if(X > this.parent_counts.get(target[0])) {
                target[0] = key;
            }
        });
        System.out.println("Finished: " + ((System.currentTimeMillis() - start) / 1000.) + "s");
        return target[0];
    }

    String instanceName;

    // Handle WebSocket Messages
    @Override
    public void onMessage(final String messageText) {
        System.out.println("Entering on_message");
        long start = System.currentTimeMillis();

        // Parse JSON
        System.out.println(messageText);
        final JSONObject message = new JSONObject(messageText);

        switch (state) {
            default:
                if (message.has("Repo")) {
                    asked = 0;

                    this.parse_dag(message);
                    this.instanceName = message.getJSONObject("Repo").getString("name");
                    this.state = State.IN_PROGRESS;
                }

                // Fetch and parse a new problem if we see key "Instance"
                if (message.has("Instance")) {
                    if(this.instanceName.contains("big")){
                        this.send("\"GiveUp\"");
                        return;
                    }
                    asked = 0;

                    this.good = message.getJSONObject("Instance").getString("good");
                    this.bad = message.getJSONObject("Instance").getString("bad");
                    this.dag = (HashMap<String, List<String>>) this.original_dag.clone();

                    this.purge_invalid_commits(good, bad);
                    this.assign_parents();
                    this.lastChoice = this.choice;
                    this.choice = this.assign_minimums(bad);
                    this.render_dag();
                    asked ++;
                    this.send(new JSONObject().put("Question", this.choice).toString());
                }

                if (message.has("Answer")) {
                    if (message.getString("Answer").equals("Good")) {
                        this.good = this.choice;
                    } else {
                        this.bad = this.choice;
                    }

                    this.purge_invalid_commits(good, bad);
                    this.assign_parents();
                    this.lastChoice = this.choice;
                    this.choice = this.assign_minimums(bad);

                    if (this.choice.equals(this.lastChoice)) {
                        System.out.println("Solution: " + this.bad);
                        this.send(new JSONObject().put("Solution", this.bad).toString());
                        this.state = State.START;
                    } else {
                        asked++;
                        System.out.println("Questions asked: " + asked);
                        if (asked > 30) {
                            this.send("\"GiveUp\"");
                        } else {
                            this.send(new JSONObject().put("Question", this.choice).toString());
                        }
                    }
                }



                break;
        }
    }



    @Override
    public void onClose(final int arg0, final String arg1, final boolean arg2) {
        System.out.printf("L: onClose(%d, %s, %b)\n", arg0, arg1, arg2);
    }

    @Override
    public void onError(final Exception arg0) {
        System.out.printf("L: onError(%s)\n", arg0);
        arg0.printStackTrace();
    }

    @Override
    public void onOpen(final ServerHandshake hs) {
        JSONArray authorization = new JSONArray(new Object[]{kentId, token});
        send(new JSONObject().put("User", authorization).toString());
    }
}
