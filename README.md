# searchEnginesProject
This project implements a text-based search engine. This system supports four retrival models: unranked boolean,
ranked boolean, BM25 and Indri. It supports both structured queries and unstructured queries (AND, OR, NEAR). 
Several optimizations are applied to this system: first one is using Pseudo-relevance feedback, second is adding 
learning to rank with support vector machine, and final one is adding intent-based diversification by xQuAD algorithm 
and PM2 algorithm. 

QryEval is the main class. Given a parameter file which specifies the index path and query file in a key value pair (e.g.,
index=path_to_index), it opens the index, evaluates the queries, and prints the results and writes results to another file.
