# Fn Democracy
Create, run and report on a simple poll. An experimental app for [Fn](http://fnproject.io) and OCI.

## Principles
  * Minimal data collection. Make it as difficult as possible to de-anonymise votes.
  * Results only visible after poll closes. No anchoring.
  * Polls run for a set time and cannot be counted until that time has elapsed.
  * Polls are immutable - no changes once it's been created.

## Features
  * Create a new poll. Give it a name a small number of options to put on the ballot. Get a single shareable, public ballot URL for participants and a result URL.
  * Vote on a poll. Visit the ballot URL from the create step above. Click on the option you wish to vote for. Get a confirmation that your vote was cast or an error.
  * Count a poll. Visit the count URL and give it a poll id. If the poll has ended then delete ballot URL, count votes and write result page, delete individual votes.

## How it works
There are 2 types of Object Storage bucket.
  1. The results bucket - a publicly readable bucket where the results of a poll are kept indefinitely.
  2. A temp bucket for each active poll that contains the ballot paper (HTML), some JSON data about the poll and the vote files. Access to this bucket is controlled by Pre-Authenticated Requests (PARs) that exist only for the duration of the poll.

### /new
#### Sample input
    {
      "name": "Display Name For Poll",
      "description": "A bit more information about your poll.",
      "options": ["List", "Of", "Candidates", "To", "Put", "On", "Ballot", "Paper"],
      "for": "1d"
    }
   
#### Overview
  * Creates a new Object Storage bucket to hold votes and ballot paper.
  * Create the ballot paper HTML and write it to the poll bucket.
  * Dump poll data as JSON and write to the poll bucket.
  * Creates a Pre-Authenticated Request (PAR) against poll bucket for accessing the ballot paper.
  * Creates a PAR against poll bucket for accessing the JSON.
  * Creates a PAR agaist poll bucket for recording a vote.

### /vote
#### Overview
  * Read the PARs from the query string.
  * Use JSON PARs to load poll data.
  * Verify vote against poll data (TODO).
  * Write vote file in poll bucket.
  * Confirm vote by reading back from poll bucket.
  * Write success message.

### /close
#### Overview
  * Exit if there are still PARs on the vote bucket.
  * Destructive read of all vote files from vote bucket and aggregate a count per option.
  * Write new result HTML with table of results to results bucket.
  * Delete ballot HTML and JSON files.
  * Delete vote bucket.
  
## Developing
  * Configure an OCI tenancy with users, groups and policies or ask me for key to mine. TODO: doc or terraform this.
  * Update config in /new to point to compartment where your buckets will live.
  * `fn deploy --all --local`

## TODO
  * Pretty the HTML
  * HTML form for /new and /close
  * Doc or terraform for OCI setup
  
  
