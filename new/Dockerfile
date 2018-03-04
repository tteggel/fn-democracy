FROM python:3.6.2

RUN mkdir /code
ADD requirements.txt /code/
WORKDIR /code/
RUN pip install -r requirements.txt
ADD . /code/

ENTRYPOINT ["python3", "func.py"]