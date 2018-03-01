
var invocation = new XMLHttpRequest();
var url = 'http://localhost:8080/r/fn-poll/vote';
var body = '{"nps": 10}';

function callOtherDomain(){
  if(invocation)
    {
      invocation.open('POST', url, true);
      invocation.onreadystatechange = handler;
      invocation.send(body); 
    }
}

function handler(){
    
}

callOtherDomain();
