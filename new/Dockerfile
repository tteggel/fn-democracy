FROM python:3.6.2

ENV http_proxy=http://www-proxy-hqdc.us.oracle.com:80
ENV https_proxy=http://www-proxy-hqdc.us.oracle.com:80

RUN mkdir /code
ADD requirements.txt /code/
WORKDIR /code/
RUN pip install -r requirements.txt
ADD . /code/

ENV http_proxy=
ENV https_proxy=

ENTRYPOINT ["python3", "func.py"]