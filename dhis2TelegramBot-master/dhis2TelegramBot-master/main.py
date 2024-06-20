from typing import Final
from telegram import Update, File
from telegram.ext import Application, CommandHandler, MessageHandler, filters, ContextTypes
import json
import requests
import tempfile
import logging


TOKEN: Final = '7326142052:AAGQGUUIHKiRkEF72EQJUwS8fmaIxQhPOz0'
BOT_USERNAME: Final = '@Dhis2ExpertsBot'

DHIS2_SERVER: Final = 'https://play.im.dhis2.org/stable-2-38-6'
DHIS2_USERNAME: Final = 'admin'
DHIS2_PASSWORD: Final = 'district'

logging.basicConfig(
    level=logging.DEBUG,  # Set the logging level to DEBUG or higher
    format='%(asctime)s - %(levelname)s - %(message)s'  # Define log message format
)


#Commands
async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text('Hello, please send me tracker app data and I will import it to the DHIS2 server for you.')

async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text('I am only here to help import the data to the server')

async def invalid_file_handler(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text('Invalid file, only .JSON files supported')

async def handle_file(update: Update, context: ContextTypes.DEFAULT_TYPE):
    document = update.message.document
    file = await context.bot.get_file(document.file_id)

    with tempfile.NamedTemporaryFile(delete=False) as tf:
        await file.download_to_drive(tf.name)
        tf.flush()
        tf.seek(0)
        json_data = json.load(tf)

    logging.info(f'Received file from user {update.message.chat.id}: {document.file_name}')
    
    try:
        response = import_to_dhis2(json_data)

        if response.status_code == 200:
            await update.message.reply_text("File successfully imported to the DHSI2 Server")
        elif response.status_code == 400:
            await update.message.reply_text("Invalid metadata sent to the server, please make sure it was exported from the DHIS2 App")
        elif response.status_code == 409:
            await update.message.reply_text("Import Error - Conflict with Existing Data - Contact Admin")
        else:
            await update.message.reply_text(f'Import Error occured, Contact Admin for assistance')
            # await update.message.reply_text(f'Failed to import file to the DHIS2 server. Status code: {response.json()}')
            logging.error(f'Failed to import file to DHIS2. Status code: {response.status_code}')
    except Exception as e:
        await update.message.reply_text('An error occurred while processing the file.')
        logging.error(f'Error processing file: {str(e)}')

def import_to_dhis2(data):
    headers = {'Content-Type': 'application/json'}
    try:
        response = requests.post(
            f'{DHIS2_SERVER}/api/metadata?atomicMode=NONE',
            auth=(DHIS2_USERNAME, DHIS2_PASSWORD),
            headers=headers,
            data=json.dumps(data)
        )
        # response.raise_for_status()  # Raise an error for bad response status codes
        logging.info(f'Sent data to DHIS2 server. Status code: {response.status_code}')
        return response
    except requests.exceptions.RequestException as e:
        logging.error(f'Error sending data to DHIS2 server: {str(e)}')
        raise  # Re-raise the exception to propagate it

#Responses
def handle_response(text: str) -> str:
    processed: str = text.lower()

    if 'hello' in processed:
        return 'Hey There'
    if 'how are you' in processed:
        return 'I am good thanks, how are you?'
        
    return 'I do not understand, I am only here to help import the data to the server.'

async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message_type: str = update.message.chat.type
    text: str = update.message.text

    print(f'User ({update.message.chat.id}) in {message_type}: "{text}"')

    response: str = handle_response(text)

    print('Bot: ', response)
    await update.message.reply_text(response)

async def error(update: Update, context: ContextTypes.DEFAULT_TYPE):
    print(f'Update {update} caused error {context.error}')

if __name__ == '__main__':
    print('Starting bot...')
    app = Application.builder().token(TOKEN).build()

    #Handlers
    start_handler = CommandHandler('start', start_command)
    help_handler = CommandHandler('help', help_command)
    file_handler = MessageHandler(filters.Document.MimeType("application/json"), handle_file)

    #Commands
    app.add_handler(start_handler)
    app.add_handler(help_handler)
    app.add_handler(file_handler)
    app.add_handler(MessageHandler(filters.TEXT, handle_message))
    app.add_handler(MessageHandler(~filters.Document.MimeType("application/json"), invalid_file_handler))

    #Messages

    #Errors
    app.add_error_handler(error)
    
    print('Polling...')
    app.run_polling(poll_interval=3)