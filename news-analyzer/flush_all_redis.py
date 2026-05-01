from redis import Redis

def flush_all():
    r = Redis(host='127.0.0.1', port=6379, db=0, decode_responses=True)
    
    print("⚠️  WARNING: This will delete EVERYTHING in Redis DB 0 (including symbols, analysis, and config)!")
    confirm = input("Type 'FLUSH' to confirm complete deletion: ")
    
    if confirm == "FLUSH":
        r.flushdb()
        print("💥 SUCCESS: Redis DB 0 has been completely flushed.")
    else:
        print("Operation cancelled.")

if __name__ == "__main__":
    flush_all()
