from redis import Redis
import sys

def clear_redis():
    r = Redis(host='127.0.0.1', port=6379, db=0, decode_responses=True)
    
    # Define the patterns we want to clear
    patterns = [
        "news:analysis:*",
        "news:results_by_api:*",
        "news:buy_list",
        "news:config:*" # Note: This will also clear your keywords!
    ]
    
    print("🧹 Starting Redis Cleanup...")
    
    total_deleted = 0
    for pattern in patterns:
        keys = r.keys(pattern)
        if keys:
            r.delete(*keys)
            print(f"  - Deleted {len(keys)} keys matching '{pattern}'")
            total_deleted += len(keys)
    
    print(f"\n✅ Done. Total keys removed: {total_deleted}")
    print("Your Redis is now clean.")

if __name__ == "__main__":
    confirm = input("This will delete all news analyzer results and configs. Are you sure? (y/n): ")
    if confirm.lower() == 'y':
        clear_redis()
    else:
        print("Cleanup cancelled.")
