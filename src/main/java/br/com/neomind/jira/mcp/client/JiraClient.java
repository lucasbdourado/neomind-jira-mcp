package br.com.neomind.jira.mcp.client;

public interface JiraClient {

    String getServerInfo();

    String getIssue(String issueKey);

    String searchIssues(String jql, Integer maxResults);

    String getIssueComments(String issueKey);
}
