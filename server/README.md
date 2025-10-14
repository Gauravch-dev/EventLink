# activate the virtual env

.venv\Scripts\activate

# set gemini api key

set GOOGLE_API_KEY=AIzaSyBCe3cyibX3OcC_k_kiwN2put3O8QBlZpQ

# run the server

uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
