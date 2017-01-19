//
//  TesseractPlugin.m
//
//  @author Gustavo Mazzoni - 2016.
//

#import "TesseractPlugin.h"
#import "claseAuxiliar.h"

@implementation TesseractPlugin
@synthesize callbackID;





- (void) recognizeText:(CDVInvokedUrlCommand*)command { //get the callback id 
    NSArray *arguments = command.arguments;
    
    NSString *language = [arguments objectAtIndex:0];
    NSLog(@"%s:%d language=%@", __func__, __LINE__, language);
    NSString *imagedata = [arguments objectAtIndex:1];


    self.callbackID = command.callbackId;

    NSData *data;

    if ([NSData instancesRespondToSelector:@selector(initWithBase64EncodedString:options:)]) {
        data = [[NSData alloc] initWithBase64EncodedString:imagedata options:kNilOptions];  // iOS 7+
    } else {
        data = [[NSData alloc] initWithBase64Encoding:imagedata];                           // pre iOS7
    }


    claseAuxiliar *cA = [[claseAuxiliar alloc]init];

    
    UIImage *Realimage = [[UIImage alloc] initWithData:data];

    
    NSString *text = [cA ocrImage:Realimage withLanguage:language];

    [self performSelectorOnMainThread:@selector(ocrProcessingFinished:)
                           withObject:text
                        waitUntilDone:NO];
    
}



- (void)ocrProcessingFinished:(NSString *)result
{
    CDVPluginResult* pluginResult;
    
    if (result == nil
        || ([result respondsToSelector:@selector(length)]
            && [(NSData *)result length] == 0)
        || ([result respondsToSelector:@selector(count)]
            && [(NSArray *)result count] == 0))
    {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"Empty result" ];
    }
    else
    {
        NSString *formatedData = [self extractData:result];
        
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: formatedData ];
    }

    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackID];
}

- (NSString *)extractData:(NSString *)inputData
{
    NSDictionary *outputData = @{};

    NSError *error = nil;
    NSDataDetector *detector = [NSDataDetector dataDetectorWithTypes:NSTextCheckingTypeLink|NSTextCheckingTypePhoneNumber|NSTextCheckingTypeAddress                                                               error:&error];
    
    NSArray *matches = [detector matchesInString:inputData
                                         options:0
                                           range:NSMakeRange(0, [inputData length])];
    
    NSString *json = @"{";
    bool isFirstElement = true;
    
    //Check Urls and add them to json
    json = [json stringByAppendingString:@"\"urls\": ["];
    for (NSTextCheckingResult *match in matches) {
        if ([match resultType] == NSTextCheckingTypeLink) {

            if (isFirstElement == true)
                isFirstElement = false;
            else
                json = [json stringByAppendingString:@", "];

            json = [json stringByAppendingString:@"\""];
            NSRange matchRange = [match range];
            
            NSURL *url = [match URL];
            json = [json stringByAppendingString:url.absoluteString];

            json = [json stringByAppendingString:@"\""];
        }
    }
    json = [json stringByAppendingString:@"], "];
    
    //Check Phones and add them to json
    json = [json stringByAppendingString:@"\"phones\": ["];
    isFirstElement = true;
    for (NSTextCheckingResult *match in matches) {
        if ([match resultType] == NSTextCheckingTypePhoneNumber) {
            if (isFirstElement == true)
                isFirstElement = false;
            else
                json = [json stringByAppendingString:@", "];

            json = [json stringByAppendingString:@"\""];
            
            NSString *phoneNumber = [match phoneNumber];
            json = [json stringByAppendingString:phoneNumber];

            json = [json stringByAppendingString:@"\""];
        }

    }
    json = [json stringByAppendingString:@"], "];
    
    //Check Addresses and add them to json
    json = [json stringByAppendingString:@"\"addresses\": ["];
    isFirstElement = true;
    for (NSTextCheckingResult *match in matches) {
        if ([match resultType] == NSTextCheckingTypeAddress) {

            if (isFirstElement == true)
                isFirstElement = false;
            else
                json = [json stringByAppendingString:@", "];

            json = [json stringByAppendingString:@"{ "];
            
            bool isFirstElementSecondLevel = true;

            NSDictionary<NSString*, NSString*> *address = [match addressComponents];
            for(id key in address)
            {
                if (isFirstElementSecondLevel == true)
                    isFirstElementSecondLevel = false;
                else
                    json = [json stringByAppendingString:@", "];

                NSString *addressValue = [NSString stringWithFormat:@"\"%@\": \"%@\"", key, [address objectForKey:key]];
                json = [json stringByAppendingString:addressValue];
            }
            
            json = [json stringByAppendingString:@"}"];
        }
    }

    NSString *escapedString = [inputData stringByReplacingOccurrencesOfString:@"\"" withString:@"\\\""];
    escapedString = [inputData stringByReplacingOccurrencesOfString:@"\n" withString:@"\\n"];
    json = [json stringByAppendingString:@"], \"raw_data\": \""];
    json = [json stringByAppendingString:escapedString];
    json = [json stringByAppendingString:@"\"}"];

    return json;
}
@end
